package com.shelved.domain.accusation.service;

import static com.shelved.common.exception.ErrorCode.ACCUSATION_LIMIT_EXCEEDED;
import static com.shelved.common.exception.ErrorCode.GRADING_RETRY_TOO_SOON;

import com.shelved.common.config.GameProperties;
import com.shelved.common.entity.Accusation;
import com.shelved.common.entity.AiRequest;
import com.shelved.common.entity.Room;
import com.shelved.common.entity.Suspect;
import com.shelved.common.entity.json.AiRequestInput;
import com.shelved.common.enums.AiRequestStatus;
import com.shelved.common.enums.AiRequestType;
import com.shelved.common.enums.GradingStatus;
import com.shelved.common.exception.CustomException;
import com.shelved.domain.accusation.dto.AccusationCreateResponse;
import com.shelved.domain.accusation.dto.AccusationFailureDetail;
import com.shelved.domain.accusation.dto.AccusationResponse;
import com.shelved.domain.accusation.dto.GradingRetryTooSoonDetail;
import com.shelved.domain.accusation.repository.AccusationRepository;
import com.shelved.domain.airequest.service.AiRequestCommand;
import com.shelved.domain.airequest.service.AiRequestHandler;
import com.shelved.domain.airequest.service.AiRequestSuccess;
import com.shelved.domain.cases.repository.SuspectRepository;
import com.shelved.domain.room.dto.RoomStateResponse.RoomUsageCount;
import com.shelved.domain.room.repository.AiRequestRepository;
import com.shelved.domain.room.service.RoomTimeCalculator;
import com.shelved.domain.room.service.RoomTimeSettler;
import com.shelved.infra.llm.LlmRequest;
import com.shelved.infra.llm.LlmResponse;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 지목(ACCUSATION)의 AiRequestHandler. 흐름·트랜잭션은 AiRequestExecutor가 정하고, 여기서는 지목 고유 부분만 한다.
 * 대상 키는 없다(세션당 동시 1건 → 409 AI_REQUEST_IN_PROGRESS).
 *
 * <p>지목 행(ACCUSATIONS)과 요청 행(AI_REQUESTS)의 상태</p>
 * <ul>
 *     <li>접수(prepare): 지목 행을 PENDING으로 저장한다. 접수 시각·접수 시점 누적 시간이 만료 보류(채점 대기)의 기준이다</li>
 *     <li>성공(complete): 지목 행 COMPLETED → 결과 반영(RoomTimeSettler.applyGradingResult)</li>
 *     <li>실패(onFailed): 요청 행이 FAILED가 되는 모든 경로(모델 오류·형식 오류·복구·세션 종료 후 도착)에서 지목 행 ERROR</li>
 *     <li>재채점(prepare): 같은 지목 행을 다시 PENDING으로. 접수 시각·누적 시간은 원래 값을 유지한다(원래 접수 시점 유효성)</li>
 *     <li>대체(prepare, 새 답안): 대체되지 않은 실패 지목의 요청 행에 superseded_at을 남긴다(지목 행은 ERROR 그대로)</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccusationHandler implements AiRequestHandler<AccusationContext> {

    private final AccusationRepository accusationRepository;
    private final AiRequestRepository aiRequestRepository;
    private final SuspectRepository suspectRepository;
    private final GradingContextLoader gradingContextLoader;
    private final AccusationGrader accusationGrader;
    private final AccusationUsageCounter accusationUsageCounter;
    private final RoomTimeSettler roomTimeSettler;
    private final GameProperties gameProperties;
    private final Clock clock;

    @Override
    public AiRequestType type() {
        return AiRequestType.ACCUSATION;
    }

    /**
     * 접수 트랜잭션 안(rooms 행 잠금). 같은 requestId의 지목 행이 있으면 재채점, 없으면 새 답안이다
     */
    @Override
    public AccusationContext prepare(Room room, AiRequestCommand command) {

        LocalDateTime now = LocalDateTime.now(clock);
        Optional<Accusation> existing = accusationRepository.findByRoomIdAndRequestId(room.getId(), command.requestId());
        if (existing.isPresent()) {
            Accusation accusation = restartGrading(room, existing.get(), now);
            GradingContext grading = loadGradingContext(
                    room, accusation.getSuspect().getId(), accusation.getTrickExplanation());
            return new AccusationContext(room.getId(), command.requestId(), accusation.getId(), grading);
        }

        validateAccusationAvailable(room.getId());
        AiRequestInput input = command.input();
        GradingContext grading = loadGradingContext(room, input.suspectId(), input.trickExplanation());
        Accusation accusation = submitNewAccusation(room, command, now);

        return new AccusationContext(room.getId(), command.requestId(), accusation.getId(), grading);
    }

    /**
     * 재채점은 연결 헤더 없이, 일시정지·마감 후에도 받는다(세션 종료 전이면). 접속 확인·재개를 하지 않는다.
     * 최소 간격은 prepare에서 검사한다
     */
    @Override
    public boolean retryRequiresPlayableRoom() {
        return false;
    }

    @Override
    public LlmRequest buildLlmRequest(AccusationContext context) {
        return accusationGrader.buildLlmRequest(context.grading());
    }

    /**
     * 확정 트랜잭션 안(rooms 행 잠금, 세션 미종료·최신 실행 확인 뒤). 응답 형식을 먼저 검증하고(오류면 아무것도 바꾸지 않고 채점 실패),
     * 지목이 아직 PENDING일 때 현재 시각으로 정산한 뒤(채점 중 시간이 다 됐으면 채점 대기 PAUSED) 채점 결과와 세션 종료를 반영한다
     */
    @Override
    public AiRequestSuccess complete(Room room, AiRequest aiRequest, AccusationContext context, LlmResponse response) {

        GradingResult result = accusationGrader.grade(context.grading(), response.text());

        LocalDateTime now = LocalDateTime.now(clock);
        roomTimeSettler.settle(room, now);

        Accusation accusation = accusationRepository.findById(context.accusationId())
                .filter(found -> found.getGradingStatus() == GradingStatus.PENDING)
                .orElseThrow(() -> new IllegalStateException("채점 중인 지목이 아닙니다. accusationId=" + context.accusationId()));
        accusation.completeGrading(result.culpritCorrect(), result.verdict(), result.elementResultList(), result.feedback());
        roomTimeSettler.applyGradingResult(room, accusation, now);

        RoomUsageCount accusationCount = accusationUsageCounter.countAccusationUsage(room.getId());
        int attemptNo = Math.toIntExact(accusationRepository.countByRoomIdAndGradingStatusAndIdLessThanEqual(
                room.getId(), GradingStatus.COMPLETED, accusation.getId()));
        AccusationResponse accusationResponse = AccusationResponse.of(
                accusation, aiRequest, attemptNo, gameProperties.gradingRetryMinInterval());

        return new AiRequestSuccess(accusation.getId(),
                new AccusationCreateResponse(accusationResponse, accusationCount, room.getStatus()));
    }

    /**
     * 요청 행이 FAILED가 된 모든 경로에서 채점 중인 지목 행을 ERROR로 맞춘다 (기회 미차감, 같은 답안으로 재채점 가능)
     */
    @Override
    public void onFailed(AiRequest aiRequest) {
        accusationRepository.findByRoomIdAndRequestId(aiRequest.getRoom().getId(), aiRequest.getRequestId())
                .filter(accusation -> accusation.getGradingStatus() == GradingStatus.PENDING)
                .ifPresent(Accusation::markGradingError);
    }

    /**
     * 502 GRADING_FAILED의 detail. 재채점 가능 시각은 요청 행의 실패 시각(updated_at) 기준이다 (AiRequestStatusResponse와 같은 값)
     */
    @Override
    public Object failureDetail(String requestId, AccusationContext context) {
        LocalDateTime retryAvailableAt = aiRequestRepository.findByRoomIdAndRequestId(context.roomId(), requestId)
                .filter(aiRequest -> aiRequest.getStatus() == AiRequestStatus.FAILED)
                .map(aiRequest -> aiRequest.getUpdatedAt().plus(gameProperties.gradingRetryMinInterval()))
                .orElse(null);
        return new AccusationFailureDetail(requestId, context.accusationId(), retryAvailableAt);
    }

    // 기회 = 정상 채점 완료(COMPLETED) 수. 채점 중(PENDING) 지목은 접수의 세션당 동시 1건 검사가 이미 막았다
    private void validateAccusationAvailable(Long roomId) {
        long completedCount = accusationRepository.countByRoomIdAndGradingStatus(roomId, GradingStatus.COMPLETED);
        if (completedCount >= gameProperties.accusationLimit()) {
            throw new CustomException(ACCUSATION_LIMIT_EXCEEDED);
        }
    }

    // 새 답안: 이전 실패 지목 대체 → PENDING 지목 저장 (인물은 채점 컨텍스트 구성에서 이 사건 소속임을 확인했다)
    private Accusation submitNewAccusation(Room room, AiRequestCommand command, LocalDateTime now) {

        supersedeFailedAccusations(room.getId(), now);

        AiRequestInput input = command.input();
        Suspect suspect = suspectRepository.getReferenceById(input.suspectId());
        long submittedActiveMs = RoomTimeCalculator.calculate(room, now).elapsedActiveMs();

        return accusationRepository.saveAndFlush(new Accusation(
                room, room.getHostUser(), suspect, command.requestId(), input.trickExplanation(), now, submittedActiveMs));
    }

    // 재채점: 이전 시도 후 최소 간격 확인 → 같은 지목 행을 PENDING으로. 기회는 접수 때 이미 확인했으므로 다시 보지 않는다
    private Accusation restartGrading(Room room, Accusation accusation, LocalDateTime now) {

        AiRequest failedRequest = aiRequestRepository.findAiRequest(room.getId(), accusation.getRequestId());
        LocalDateTime retryAvailableAt = failedRequest.getUpdatedAt().plus(gameProperties.gradingRetryMinInterval());
        if (now.isBefore(retryAvailableAt)) {
            throw new CustomException(GRADING_RETRY_TOO_SOON, new GradingRetryTooSoonDetail(retryAvailableAt));
        }

        accusation.restartGrading();

        return accusation;
    }

    private void supersedeFailedAccusations(Long roomId, LocalDateTime now) {
        aiRequestRepository.findByRoomIdAndRequestTypeAndStatus(roomId, AiRequestType.ACCUSATION, AiRequestStatus.FAILED).stream()
                .filter(failed -> failed.getSupersededAt() == null)
                .forEach(failed -> failed.supersede(now));
    }

    // 시드 오류(IllegalStateException)는 접수 단계의 500으로 남긴다. 로그에는 사건·세션 ID와 구분만 남기고 답안은 넣지 않는다
    private GradingContext loadGradingContext(Room room, Long suspectId, String trickExplanation) {
        try {
            return gradingContextLoader.load(room, suspectId, trickExplanation);
        } catch (IllegalStateException e) {
            log.error("채점 컨텍스트 구성 실패 caseId={} roomId={} reason={}",
                    room.getMysteryCase().getId(), room.getId(), e.getMessage());
            throw e;
        }
    }
}
