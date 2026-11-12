package com.shelved.domain.airequest.service;

import static com.shelved.common.exception.ErrorCode.AI_REQUEST_ID_REUSED;
import static com.shelved.common.exception.ErrorCode.AI_REQUEST_IN_PROGRESS;
import static com.shelved.common.exception.ErrorCode.AI_REQUEST_NOT_FOUND;
import static com.shelved.common.exception.ErrorCode.AI_REQUEST_SUPERSEDED;
import static com.shelved.common.exception.ErrorCode.ROOM_ENDED_DURING_REQUEST;

import com.shelved.common.config.GameProperties;
import com.shelved.common.entity.AiRequest;
import com.shelved.common.entity.Room;
import com.shelved.common.enums.AiRequestStatus;
import com.shelved.common.enums.AiRequestType;
import com.shelved.common.exception.CustomException;
import com.shelved.common.exception.ErrorCode;
import com.shelved.common.jwt.AuthUser;
import com.shelved.domain.airequest.dto.AiRequestFailureDetail;
import com.shelved.domain.airequest.dto.AiRequestStatusResponse;
import com.shelved.domain.airequest.service.AiRequestAcceptance.InProgress;
import com.shelved.domain.airequest.service.AiRequestAcceptance.Replayed;
import com.shelved.domain.airequest.service.AiRequestAcceptance.Started;
import com.shelved.domain.room.dto.RoomStatusFilter;
import com.shelved.domain.room.repository.AiRequestRepository;
import com.shelved.domain.room.repository.RoomRepository;
import com.shelved.domain.room.service.RoomAccessService;
import com.shelved.domain.room.service.RoomPlayService;
import com.shelved.infra.llm.LlmResponse;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * AI 요청의 짧은 트랜잭션들 (접수·확정·실패·복구). AI 호출은 여기서 하지 않는다 (backend.md 13).
 * 모든 메서드는 먼저 rooms 행을 PESSIMISTIC_WRITE로 잠근 뒤 AI_REQUESTS를 읽는다. 같은 세션의 접수·확정·실패·복구와
 * 세션 상태 변경(일시정지·만료 등)이 한 번에 하나씩 처리되므로, 같은 requestId 동시 접수나 같은 대상 동시 요청도 하나만 PROCESSING이 된다.
 * 잠금은 각 트랜잭션이 끝나면 풀리므로 AI 호출 중에는 잡혀 있지 않다
 */
@Service
@RequiredArgsConstructor
public class AiRequestTransactionService {

    private final AiRequestRepository aiRequestRepository;
    private final RoomRepository roomRepository;
    private final RoomAccessService roomAccessService;
    private final RoomPlayService roomPlayService;
    private final GameProperties gameProperties;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    /**
     * 접수 (docs/api/README.md AI 요청 처리). 같은 requestId 판정 → 플레이 공통 검사 → 대상별 동시 1건 → 도메인 검사·컨텍스트 → 접수·재실행
     */
    @Transactional
    public <C> AiRequestAcceptance<C> accept(AuthUser authUser, AiRequestCommand command, AiRequestHandler<C> handler) {

        Room room = roomAccessService.getAccessibleRoomForUpdate(command.roomId(), authUser);
        AiRequestType requestType = handler.type();

        Optional<AiRequest> existing = aiRequestRepository.findByRoomIdAndRequestId(room.getId(), command.requestId());
        if (existing.isPresent()) {
            AiRequest aiRequest = existing.get();
            // 입력이 다르거나, 같은 requestId를 다른 유형·대상에 이미 썼으면 거부 (세션 안에서 유일)
            if (!aiRequest.isSameRequest(requestType, command.targetId(), command.input())) {
                throw new CustomException(AI_REQUEST_ID_REUSED);
            }
            if (aiRequest.getStatus() == AiRequestStatus.SUCCEEDED) {
                return new Replayed<>(aiRequest.getResultJson());
            }
            if (aiRequest.getStatus() == AiRequestStatus.PROCESSING) {
                return new InProgress<>(statusOf(aiRequest));
            }
            if (aiRequest.getSupersededAt() != null) {
                throw new CustomException(AI_REQUEST_SUPERSEDED);
            }
        }

        roomPlayService.acquirePlayableRoom(room.getId(), authUser, command.connectionId());
        validateTargetIdle(room.getId(), requestType, command.targetId());
        C context = handler.prepare(room, command);

        LocalDateTime now = LocalDateTime.now(clock);
        AiRequest aiRequest;
        if (existing.isPresent()) {
            aiRequest = existing.get();
            aiRequest.restart(now);
        } else {
            aiRequest = aiRequestRepository.saveAndFlush(new AiRequest(
                    room, command.requestId(), requestType, command.targetId(), command.input(), now));
        }

        return new Started<>(new AiRequestExecution<>(
                room.getId(),
                room.getMysteryCase().getId(),
                aiRequest.getId(),
                aiRequest.getRequestId(),
                aiRequest.getExecutionNo(),
                context
        ));
    }

    /**
     * 결과 확정. 세션 종료 → 늦은 응답 순으로 확인한 뒤 도메인이 게임 데이터를 저장하고, 최초 성공 응답을 스냅샷으로 남긴다.
     * 일시정지 중 도착한 응답은 정상 저장한다. 도메인 검증 실패(AiResponseInvalidException)는 전체를 롤백한다
     */
    @Transactional
    public <C> AiRequestCompletion complete(AiRequestExecution<C> execution, AiRequestHandler<C> handler, LlmResponse response) {

        Room room = roomRepository.findRoomWithLockById(execution.roomId());
        AiRequest aiRequest = findAiRequest(execution.aiRequestId());
        boolean currentExecution = aiRequest.isCurrentExecution(execution.executionNo());
        LocalDateTime now = LocalDateTime.now(clock);

        if (RoomStatusFilter.isEnded(room.getStatus())) {
            if (currentExecution) {
                aiRequest.fail(ROOM_ENDED_DURING_REQUEST, now);
            }
            return new AiRequestCompletion.RoomEnded();
        }
        if (!currentExecution) {
            return new AiRequestCompletion.Stale();
        }

        AiRequestSuccess success = handler.complete(room, aiRequest, execution.context(), response);
        JsonNode snapshot = jsonMapper.valueToTree(success.response());
        aiRequest.succeed(success.resultRefId(), snapshot, now);

        return new AiRequestCompletion.Succeeded(snapshot);
    }

    /**
     * 실행 실패. 최신 실행이고 PROCESSING일 때만 FAILED로 바꾸며(임시 확보 해제), 이전 실행의 늦은 실패는 무시한다
     *
     * @return 반영했으면 true
     */
    @Transactional
    public boolean fail(AiRequestExecution<?> execution, ErrorCode errorCode) {

        roomRepository.findRoomWithLockById(execution.roomId());
        AiRequest aiRequest = findAiRequest(execution.aiRequestId());
        if (!aiRequest.isCurrentExecution(execution.executionNo())) {
            return false;
        }

        aiRequest.fail(errorCode, LocalDateTime.now(clock));

        return true;
    }

    /**
     * 작업 시간 제한·서버 재시작 복구. 아직 PROCESSING이고 (기준이 있으면) 마지막 갱신이 기준 이전일 때만 유형별 실패 코드로 정리한다
     *
     * @param updatedBefore 이 시각 이후 갱신(재시도 등)된 요청은 건너뛴다. null이면 시각과 관계없이 정리한다
     * @return 정리했으면 true
     */
    @Transactional
    public boolean failProcessing(Long aiRequestId, LocalDateTime updatedBefore) {

        Optional<Long> roomId = aiRequestRepository.findRoomIdById(aiRequestId);
        if (roomId.isEmpty()) {
            return false;
        }
        roomRepository.findRoomWithLockById(roomId.get());

        AiRequest aiRequest = findAiRequest(aiRequestId);
        if (aiRequest.getStatus() != AiRequestStatus.PROCESSING) {
            return false;
        }
        if (updatedBefore != null && aiRequest.getUpdatedAt().isAfter(updatedBefore)) {
            return false;
        }

        aiRequest.fail(aiRequest.getRequestType().getFailureCode(), LocalDateTime.now(clock));

        return true;
    }

    /**
     * 이 실행의 결과를 반영하지 못했을 때(늦은 응답) 요청의 현재 상태로 응답한다
     */
    @Transactional(readOnly = true)
    public <C> AiRequestResult currentResult(AiRequestExecution<C> execution, AiRequestHandler<C> handler) {

        AiRequest aiRequest = findAiRequest(execution.aiRequestId());

        return switch (aiRequest.getStatus()) {
            case SUCCEEDED -> new AiRequestResult.Completed(aiRequest.getResultJson());
            case PROCESSING -> new AiRequestResult.Processing(statusOf(aiRequest));
            case FAILED -> throw failedException(aiRequest, handler, execution.context());
        };
    }

    private void validateTargetIdle(Long roomId, AiRequestType requestType, Long targetId) {
        aiRequestRepository.findByRoomIdAndRequestTypeAndStatus(roomId, requestType, AiRequestStatus.PROCESSING).stream()
                .filter(processing -> Objects.equals(processing.getTargetId(), targetId))
                .findFirst()
                .ifPresent(processing -> {
                    throw new CustomException(AI_REQUEST_IN_PROGRESS, new AiRequestFailureDetail(processing.getRequestId()));
                });
    }

    private AiRequest findAiRequest(Long aiRequestId) {
        return aiRequestRepository.findById(aiRequestId)
                .orElseThrow(() -> new CustomException(AI_REQUEST_NOT_FOUND));
    }

    private AiRequestStatusResponse statusOf(AiRequest aiRequest) {
        return AiRequestStatusResponse.from(aiRequest, gameProperties.gradingRetryMinInterval());
    }

    private static <C> CustomException failedException(AiRequest aiRequest, AiRequestHandler<C> handler, C context) {
        if (ROOM_ENDED_DURING_REQUEST.name().equals(aiRequest.getErrorCode())) {
            return new CustomException(ROOM_ENDED_DURING_REQUEST);
        }
        return new CustomException(handler.type().getFailureCode(), handler.failureDetail(aiRequest.getRequestId(), context));
    }
}
