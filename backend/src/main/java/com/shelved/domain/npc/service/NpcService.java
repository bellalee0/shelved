package com.shelved.domain.npc.service;

import com.shelved.common.entity.Room;
import com.shelved.common.entity.Suspect;
import com.shelved.common.entity.json.AiRequestInput;
import com.shelved.common.enums.AiRequestType;
import com.shelved.common.jwt.AuthUser;
import com.shelved.domain.airequest.dto.AiRequestStatusResponse;
import com.shelved.domain.airequest.service.AiRequestCommand;
import com.shelved.domain.airequest.service.AiRequestExecutor;
import com.shelved.domain.airequest.service.AiRequestPrecheckService;
import com.shelved.domain.airequest.service.AiRequestQueryService;
import com.shelved.domain.airequest.service.AiRequestResult;
import com.shelved.domain.airequest.service.QuestionFilterService;
import com.shelved.domain.cases.repository.SuspectRepository;
import com.shelved.domain.npc.dto.NpcAskRequest;
import com.shelved.domain.npc.dto.NpcMessageListResponse;
import com.shelved.domain.npc.dto.NpcMessageResponse;
import com.shelved.domain.npc.repository.NpcConversationRepository;
import com.shelved.domain.npc.repository.NpcMessageRepository;
import com.shelved.domain.room.service.RoomAccessService;
import com.shelved.infra.llm.LlmCallContext;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NpcService {

    private final AiRequestPrecheckService aiRequestPrecheckService;
    private final NpcAskPrecheckService npcAskPrecheckService;
    private final QuestionFilterService questionFilterService;
    private final AiRequestExecutor aiRequestExecutor;
    private final AiRequestQueryService aiRequestQueryService;
    private final NpcAskHandler npcAskHandler;
    private final RoomAccessService roomAccessService;
    private final SuspectRepository suspectRepository;
    private final NpcConversationRepository npcConversationRepository;
    private final NpcMessageRepository npcMessageRepository;

    /**
     * NPC 자유 질문. 트랜잭션 없이 실행한다 (분류·답변 AI 호출 중 트랜잭션·잠금을 잡지 않음, backend.md 13)
     *
     * <ol>
     *     <li>같은 requestId가 이미 성공·처리 중이면 분류·사전 검사 없이 접수 단계로 보내 저장된 결과(200)·처리 상태(202)를 받는다.
     *     입력이 다르면 409 AI_REQUEST_ID_REUSED (읽기 전용 조회)</li>
     *     <li>새 요청·실패 재시도: 플레이 공통 검사·인물 검사(짧은 트랜잭션) → 질문 분류(트랜잭션 밖, 차단 400·실패 502)</li>
     *     <li>AiRequestExecutor: 접수(최종 검사·대상별 동시 1건) → 답변 생성 → 확정</li>
     * </ol>
     * 차단·분류 실패 시 AI_REQUESTS 행과 대화 기록을 만들지 않는다
     */
    public AiRequestResult askNpc(
            AuthUser authUser,
            Long roomId,
            Long suspectId,
            NpcAskRequest request,
            String connectionId
    ) {
        AiRequestCommand command = new AiRequestCommand(
                roomId, request.requestId(), suspectId, AiRequestInput.question(request.question()), connectionId);

        if (!aiRequestPrecheckService.isAlreadyAccepted(authUser, command, AiRequestType.NPC_ASK)) {
            Long caseId = npcAskPrecheckService.validateAskable(authUser, roomId, suspectId, connectionId);
            questionFilterService.validateQuestion(request.question(), request.requestId(),
                    LlmCallContext.game(roomId, caseId), AiRequestType.NPC_ASK.getFailureCode());
        }

        return aiRequestExecutor.execute(authUser, command, npcAskHandler);
    }

    /**
     * 한 NPC와의 대화 원문. 참가자만, 모든 세션 상태에서 조회한다. 대화가 없으면 messages는 []
     */
    @Transactional(readOnly = true)
    public NpcMessageListResponse getNpcMessageList(AuthUser authUser, Long roomId, Long suspectId) {

        Room room = roomAccessService.getAccessibleRoom(roomId, authUser);
        Suspect suspect = suspectRepository.findSuspectOfCase(suspectId, room.getMysteryCase().getId());

        List<NpcMessageResponse> messageList = npcConversationRepository.findByRoomIdAndSuspectId(roomId, suspectId)
                .map(conversation -> npcMessageRepository.findByConversationIdOrderBySequenceNoAsc(conversation.getId()))
                .orElse(List.of())
                .stream()
                .map(NpcMessageResponse::from)
                .toList();

        List<AiRequestStatusResponse> pendingRequestList = aiRequestQueryService.getActiveAiRequestList(roomId).stream()
                .filter(status -> status.type() == AiRequestType.NPC_ASK && Objects.equals(status.targetId(), suspectId))
                .toList();

        return new NpcMessageListResponse(suspect.getId(), suspect.getName(), messageList, pendingRequestList);
    }
}
