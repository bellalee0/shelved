package com.shelved.domain.npc.service;

import com.shelved.common.config.LlmProperties;
import com.shelved.common.entity.json.GlossaryTerm;
import com.shelved.common.enums.Difficulty;
import com.shelved.common.enums.LlmRequestType;
import com.shelved.common.enums.MessageSenderType;
import com.shelved.infra.llm.LlmMessage;
import com.shelved.infra.llm.LlmModelTier;
import com.shelved.infra.llm.LlmRequest;
import com.shelved.infra.llm.PromptTemplateLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * NPC 답변 요청(LlmRequest) 구성. DB를 읽지 않고 컨텍스트만 쓴다 (트랜잭션 밖에서 호출).
 *
 * <ul>
 *     <li>시스템 프롬프트 = npc-system.txt (공통 원칙) + 난이도별 응답 방식 조각 + 인물 이름 + 용어집(표기 일관성) + persona_prompt</li>
 *     <li>메시지 = 이 NPC와의 대화 이력(USER/NPC → user/assistant 교대) + 이번 질문</li>
 *     <li>입력이 shelved.llm.max-input-tokens를 넘으면 더 자르지 않는다. LLM 클라이언트가 LLM_INPUT_TOO_LARGE로 실패시키고
 *     502 NPC_ANSWER_FAILED가 된다</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class NpcPromptBuilder {

    static final String SYSTEM_TEMPLATE_NAME = "npc-system.txt";
    private static final String NO_GLOSSARY = "(없음)";

    private final PromptTemplateLoader promptTemplateLoader;
    private final LlmProperties llmProperties;

    public LlmRequest build(NpcAskContext context) {

        String systemPrompt = promptTemplateLoader.render(SYSTEM_TEMPLATE_NAME, Map.of(
                "suspectName", context.suspectName(),
                "difficultyGuide", promptTemplateLoader.load(difficultyTemplateName(context.difficulty())).strip(),
                "glossary", glossaryText(context.glossary()),
                "personaPrompt", context.personaPrompt()
        ));

        return new LlmRequest(
                LlmRequestType.NPC_CHAT,
                LlmModelTier.MAIN,
                systemPrompt,
                messageListOf(context),
                llmProperties.maxOutputTokens()
        );
    }

    static String difficultyTemplateName(Difficulty difficulty) {
        return switch (difficulty) {
            case EASY -> "npc-difficulty-easy.txt";
            case NORMAL -> "npc-difficulty-normal.txt";
            case HARD -> "npc-difficulty-hard.txt";
        };
    }

    // 메시지는 user로 시작하고 교대해야 하므로 이력 맨 앞의 NPC 발화는 뺀다 (질문·답변은 항상 한 쌍으로 저장되어 보통은 없다)
    private static List<LlmMessage> messageListOf(NpcAskContext context) {
        List<LlmMessage> messageList = new ArrayList<>();
        for (NpcAskContext.HistoryMessage history : context.historyList()) {
            if (messageList.isEmpty() && history.senderType() == MessageSenderType.NPC) {
                continue;
            }
            messageList.add(history.senderType() == MessageSenderType.USER
                    ? LlmMessage.user(history.content())
                    : LlmMessage.assistant(history.content()));
        }
        messageList.add(LlmMessage.user(context.question()));
        return messageList;
    }

    private static String glossaryText(List<GlossaryTerm> glossary) {
        if (glossary.isEmpty()) {
            return NO_GLOSSARY;
        }
        return glossary.stream()
                .map(term -> "- " + term.original() + " → " + term.korean()
                        + (term.note() == null || term.note().isBlank() ? "" : " (" + term.note() + ")"))
                .collect(Collectors.joining("\n"));
    }
}
