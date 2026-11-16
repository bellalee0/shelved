package com.shelved.domain.npc.service;

import com.shelved.common.config.NpcProperties;
import com.shelved.common.entity.AiRequest;
import com.shelved.common.entity.MysteryCase;
import com.shelved.common.entity.NpcConversation;
import com.shelved.common.entity.NpcMessage;
import com.shelved.common.entity.Room;
import com.shelved.common.entity.Suspect;
import com.shelved.common.enums.AiRequestType;
import com.shelved.common.enums.MessageSenderType;
import com.shelved.domain.airequest.service.AiRequestCommand;
import com.shelved.domain.airequest.service.AiRequestHandler;
import com.shelved.domain.airequest.service.AiRequestSuccess;
import com.shelved.domain.airequest.service.AiResponseInvalidException;
import com.shelved.domain.cases.repository.SuspectRepository;
import com.shelved.domain.npc.dto.NpcAskResponse;
import com.shelved.domain.npc.dto.NpcMessageResponse;
import com.shelved.domain.npc.repository.NpcConversationRepository;
import com.shelved.domain.npc.repository.NpcMessageRepository;
import com.shelved.infra.llm.LlmRequest;
import com.shelved.infra.llm.LlmResponse;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;

/**
 * NPC 질문(NPC_ASK)의 AiRequestHandler. 흐름·트랜잭션은 AiRequestExecutor가 정하고, 여기서는 NPC 고유 부분만 한다.
 *
 * <p>대화 이력은 최근 shelved.npc.history-message-limit개만 프롬프트에 넣는다(의도된 설계). 대화가 길어질수록 입력 토큰·비용·응답 시간이
 * 계속 늘어나는 것을 막고, 오래된 대화보다 인물 설정(persona)을 기준으로 답하게 하기 위함이다. 한도 안의 이력으로도 입력 한도를 넘으면
 * 더 자르지 않고 LLM_INPUT_TOO_LARGE(→ 502 NPC_ANSWER_FAILED)로 실패한다.</p>
 */
@Component
@RequiredArgsConstructor
public class NpcAskHandler implements AiRequestHandler<NpcAskContext> {

    // 1~4문장 구어체 답변에 비해 충분히 큰 상한. 넘으면 형식 오류로 본다
    static final int ANSWER_MAX_LENGTH = 2000;

    private final SuspectRepository suspectRepository;
    private final NpcConversationRepository npcConversationRepository;
    private final NpcMessageRepository npcMessageRepository;
    private final NpcSuspectValidator npcSuspectValidator;
    private final NpcPromptBuilder npcPromptBuilder;
    private final NpcProperties npcProperties;
    private final Clock clock;

    @Override
    public AiRequestType type() {
        return AiRequestType.NPC_ASK;
    }

    /**
     * 접수 트랜잭션 안. 인물을 다시 확인하고(사전 검사 뒤 바뀌었을 수 있음) 프롬프트 재료를 읽는다
     */
    @Override
    public NpcAskContext prepare(Room room, AiRequestCommand command) {

        MysteryCase mysteryCase = room.getMysteryCase();
        Suspect suspect = suspectRepository.findSuspectOfCase(command.targetId(), mysteryCase.getId());
        npcSuspectValidator.validateConversable(suspect);

        List<NpcAskContext.HistoryMessage> historyList = npcConversationRepository
                .findByRoomIdAndSuspectId(room.getId(), suspect.getId())
                .map(conversation -> recentHistoryOf(conversation.getId()))
                .orElse(List.of());

        return new NpcAskContext(
                command.requestId(),
                suspect.getId(),
                suspect.getName(),
                suspect.getPersonaPrompt(),
                mysteryCase.getDifficulty(),
                mysteryCase.getGlossary(),
                historyList,
                command.input().question()
        );
    }

    @Override
    public LlmRequest buildLlmRequest(NpcAskContext context) {
        return npcPromptBuilder.build(context);
    }

    /**
     * 확정 트랜잭션 안(rooms 행 잠금). 답변을 검증하고 질문·답변을 연속된 sequence_no로 한 번에 저장한다.
     * 질문 시각은 이번 실행을 접수한 시각(재시도면 재접수 시각), 답변 시각은 확정 시각이다
     */
    @Override
    public AiRequestSuccess complete(Room room, AiRequest aiRequest, NpcAskContext context, LlmResponse response) {

        String answer = validateAnswer(response.text());
        LocalDateTime now = LocalDateTime.now(clock);

        NpcConversation conversation = npcConversationRepository.findByRoomIdAndSuspectId(room.getId(), context.suspectId())
                .orElseGet(() -> npcConversationRepository.save(
                        new NpcConversation(room, suspectRepository.getReferenceById(context.suspectId()))));
        int lastSequenceNo = npcMessageRepository.findMaxSequenceNo(conversation.getId()).orElse(0);

        NpcMessage questionMessage = new NpcMessage(conversation, MessageSenderType.USER, room.getHostUser(),
                context.question(), lastSequenceNo + 1, aiRequest.getUpdatedAt());
        NpcMessage answerMessage = new NpcMessage(conversation, MessageSenderType.NPC, null,
                answer, lastSequenceNo + 2, now);
        npcMessageRepository.saveAll(List.of(questionMessage, answerMessage));
        npcMessageRepository.flush();

        NpcAskResponse askResponse = new NpcAskResponse(
                context.requestId(),
                context.suspectId(),
                NpcMessageResponse.from(questionMessage),
                NpcMessageResponse.from(answerMessage)
        );

        return new AiRequestSuccess(answerMessage.getId(), askResponse);
    }

    /**
     * 답변 형식 검증. 비었거나 공백뿐이거나 ANSWER_MAX_LENGTH를 넘으면 형식 오류(AI 호출 실패로 처리). 앞뒤 공백은 제거해 저장한다
     */
    static String validateAnswer(String text) {
        if (text == null || text.isBlank()) {
            throw new AiResponseInvalidException("NPC 답변이 비어 있음");
        }
        String answer = text.strip();
        if (answer.length() > ANSWER_MAX_LENGTH) {
            throw new AiResponseInvalidException("NPC 답변이 너무 김 length=" + answer.length());
        }
        return answer;
    }

    private List<NpcAskContext.HistoryMessage> recentHistoryOf(Long conversationId) {
        List<NpcMessage> recentFirst = npcMessageRepository.findByConversationIdOrderBySequenceNoDesc(
                conversationId, Limit.of(npcProperties.historyMessageLimit()));
        return recentFirst.reversed().stream()
                .map(message -> new NpcAskContext.HistoryMessage(message.getSenderType(), message.getContent()))
                .toList();
    }
}
