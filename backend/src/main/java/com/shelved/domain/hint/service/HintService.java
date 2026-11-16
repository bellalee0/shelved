package com.shelved.domain.hint.service;

import com.shelved.common.entity.Hint;
import com.shelved.common.entity.Room;
import com.shelved.common.entity.json.AiRequestInput;
import com.shelved.common.enums.AiRequestStatus;
import com.shelved.common.enums.AiRequestType;
import com.shelved.common.jwt.AuthUser;
import com.shelved.domain.airequest.dto.AiRequestStatusResponse;
import com.shelved.domain.airequest.service.AiRequestCommand;
import com.shelved.domain.airequest.service.AiRequestExecutor;
import com.shelved.domain.airequest.service.AiRequestPrecheckService;
import com.shelved.domain.airequest.service.AiRequestQueryService;
import com.shelved.domain.airequest.service.AiRequestResult;
import com.shelved.domain.airequest.service.QuestionFilterService;
import com.shelved.domain.hint.dto.HintCreateRequest;
import com.shelved.domain.hint.dto.HintFollowupRequest;
import com.shelved.domain.hint.dto.HintFollowupResponse;
import com.shelved.domain.hint.dto.HintListResponse;
import com.shelved.domain.hint.dto.HintResponse;
import com.shelved.domain.hint.repository.HintFollowupRepository;
import com.shelved.domain.hint.repository.HintRepository;
import com.shelved.domain.room.service.RoomAccessService;
import com.shelved.infra.llm.LlmCallContext;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HintService {

    private final AiRequestPrecheckService aiRequestPrecheckService;
    private final HintPrecheckService hintPrecheckService;
    private final QuestionFilterService questionFilterService;
    private final AiRequestExecutor aiRequestExecutor;
    private final AiRequestQueryService aiRequestQueryService;
    private final HintHandler hintHandler;
    private final HintFollowupHandler hintFollowupHandler;
    private final HintUsageCounter hintUsageCounter;
    private final RoomAccessService roomAccessService;
    private final HintRepository hintRepository;
    private final HintFollowupRepository hintFollowupRepository;

    /**
     * 새 힌트 요청. 트랜잭션 없이 실행한다 (분류·답변 AI 호출 중 트랜잭션·잠금을 잡지 않음, backend.md 13)
     *
     * <ol>
     *     <li>같은 requestId가 이미 성공·처리 중이면 분류·사전 검사 없이 접수 단계로 보내 저장된 결과(200)·처리 상태(202)를 받는다.
     *     한도를 다 쓴 뒤의 재전송도 저장된 스냅샷을 받는다. 입력이 다르면 409 AI_REQUEST_ID_REUSED</li>
     *     <li>새 요청·실패 재시도: 플레이 공통 검사·남은 힌트(짧은 트랜잭션) → 질문 분류(트랜잭션 밖, 차단 400·실패 502)</li>
     *     <li>AiRequestExecutor: 접수(최종 검사·세션당 동시 1건·한도) → 힌트 생성 → 확정(1회 사용)</li>
     * </ol>
     * 차단·분류 실패 시 AI_REQUESTS 행과 힌트를 만들지 않는다 (차감 없음)
     */
    public AiRequestResult createHint(AuthUser authUser, Long roomId, HintCreateRequest request, String connectionId) {

        AiRequestCommand command = new AiRequestCommand(
                roomId, request.requestId(), null, AiRequestInput.question(request.question()), connectionId);

        if (!aiRequestPrecheckService.isAlreadyAccepted(authUser, command, AiRequestType.HINT)) {
            Long caseId = hintPrecheckService.validateHintRequestable(authUser, roomId, connectionId);
            questionFilterService.validateQuestion(request.question(), request.requestId(),
                    LlmCallContext.game(roomId, caseId), AiRequestType.HINT.getFailureCode());
        }

        return aiRequestExecutor.execute(authUser, command, hintHandler);
    }

    /**
     * 무료 보충 질문. 새 힌트와 같은 순서로 처리하며, 힌트 횟수는 검사·차감하지 않는다.
     * 이 세션의 힌트가 아니면 404 HINT_NOT_FOUND, 이미 썼으면 409 HINT_FOLLOWUP_ALREADY_USED (분류 전 사전 검사와 접수에서 모두 확인)
     */
    public AiRequestResult createHintFollowup(
            AuthUser authUser,
            Long roomId,
            Long hintId,
            HintFollowupRequest request,
            String connectionId
    ) {
        AiRequestCommand command = new AiRequestCommand(
                roomId, request.requestId(), hintId, AiRequestInput.question(request.question()), connectionId);

        if (!aiRequestPrecheckService.isAlreadyAccepted(authUser, command, AiRequestType.HINT_FOLLOWUP)) {
            Long caseId = hintPrecheckService.validateFollowupRequestable(authUser, roomId, hintId, connectionId);
            questionFilterService.validateQuestion(request.question(), request.requestId(),
                    LlmCallContext.game(roomId, caseId), AiRequestType.HINT_FOLLOWUP.getFailureCode());
        }

        return aiRequestExecutor.execute(authUser, command, hintFollowupHandler);
    }

    /**
     * 힌트·보충 질문 기록. 참가자만, 모든 세션 상태에서 조회한다. 힌트·보충 질문·AI 요청을 각각 한 번에 읽어 묶는다
     */
    @Transactional(readOnly = true)
    public HintListResponse getHintList(AuthUser authUser, Long roomId) {

        Room room = roomAccessService.getAccessibleRoom(roomId, authUser);

        Map<Long, HintFollowupResponse> followupByHintId = hintFollowupRepository.findAllByRoomId(roomId).stream()
                .map(HintFollowupResponse::from)
                .collect(Collectors.toMap(HintFollowupResponse::hintId, Function.identity()));

        List<AiRequestStatusResponse> pendingRequestList = aiRequestQueryService.getActiveAiRequestList(roomId).stream()
                .filter(status -> status.type() == AiRequestType.HINT || status.type() == AiRequestType.HINT_FOLLOWUP)
                .toList();
        Set<Long> processingFollowupHintIdSet = pendingRequestList.stream()
                .filter(status -> status.type() == AiRequestType.HINT_FOLLOWUP && status.status() == AiRequestStatus.PROCESSING)
                .map(AiRequestStatusResponse::targetId)
                .collect(Collectors.toSet());

        List<HintResponse> hintList = hintRepository.findByRoomIdOrderByCreatedAtAscIdAsc(roomId).stream()
                .map(hint -> hintResponseOf(hint, followupByHintId, processingFollowupHintIdSet))
                .toList();

        return new HintListResponse(
                hintUsageCounter.countHintUsage(roomId, room.getMysteryCase().getDifficulty()),
                hintList,
                pendingRequestList
        );
    }

    private static HintResponse hintResponseOf(
            Hint hint,
            Map<Long, HintFollowupResponse> followupByHintId,
            Set<Long> processingFollowupHintIdSet
    ) {
        HintFollowupResponse followup = followupByHintId.get(hint.getId());
        boolean followupAvailable = followup == null && !processingFollowupHintIdSet.contains(hint.getId());
        return new HintResponse(hint, followup, followupAvailable);
    }
}
