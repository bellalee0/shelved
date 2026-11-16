package com.shelved.domain.hint.service;

import com.shelved.common.config.LlmProperties;
import com.shelved.common.enums.LlmRequestType;
import com.shelved.infra.llm.GlossaryPromptFormatter;
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
 * 새 힌트·보충 질문의 LlmRequest 구성. DB를 읽지 않고 컨텍스트만 쓴다 (트랜잭션 밖에서 호출).
 *
 * <ul>
 *     <li>새 힌트: 시스템 프롬프트 = hint-system.txt + 사건 정보·정답 참고·수사 기록·이전 힌트, 메시지 = 이번 질문</li>
 *     <li>보충 질문: 시스템 프롬프트 = hint-followup-system.txt + 용어집·원래 질문·힌트 답변, 메시지 = 보충 질문.
 *     정답·트릭·NPC 설정·수사 기록은 넣지 않는다</li>
 *     <li>NPC 진술은 "○○의 진술"로 출처를 붙인다</li>
 *     <li>입력이 shelved.llm.max-input-tokens를 넘으면 더 자르지 않는다. LLM 클라이언트가 LLM_INPUT_TOO_LARGE로 실패시키고
 *     502 HINT_GENERATION_FAILED·HINT_FOLLOWUP_FAILED가 된다</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class HintPromptBuilder {

    static final String HINT_TEMPLATE_NAME = "hint-system.txt";
    static final String FOLLOWUP_TEMPLATE_NAME = "hint-followup-system.txt";
    static final String NONE = "(없음)";

    private final PromptTemplateLoader promptTemplateLoader;
    private final LlmProperties llmProperties;

    public LlmRequest buildHint(HintContext context) {

        HintContext.CaseInfo caseInfo = context.caseInfo();
        String systemPrompt = promptTemplateLoader.render(HINT_TEMPLATE_NAME, Map.of(
                "caseOverview", caseOverviewText(caseInfo),
                "glossary", GlossaryPromptFormatter.format(caseInfo.glossary()),
                "suspects", suspectsText(caseInfo.suspectList()),
                "evidences", evidencesText(caseInfo.evidenceList()),
                "solution", solutionText(caseInfo),
                "inspectedEvidences", bulletsOrNone(context.inspectedEvidenceList()),
                "conversations", conversationsText(context.conversationList()),
                "previousHints", previousHintsText(context.previousHintList())
        ));

        return new LlmRequest(
                LlmRequestType.HINT,
                LlmModelTier.MAIN,
                systemPrompt,
                List.of(LlmMessage.user(context.question())),
                llmProperties.maxOutputTokens()
        );
    }

    public LlmRequest buildFollowup(HintFollowupContext context) {

        String systemPrompt = promptTemplateLoader.render(FOLLOWUP_TEMPLATE_NAME, Map.of(
                "glossary", GlossaryPromptFormatter.format(context.glossary()),
                "hintQuestion", context.hintQuestion(),
                "hintContent", context.hintContent()
        ));

        return new LlmRequest(
                LlmRequestType.HINT_FOLLOWUP,
                LlmModelTier.MAIN,
                systemPrompt,
                List.of(LlmMessage.user(context.followupQuestion())),
                llmProperties.maxOutputTokens()
        );
    }

    private static String caseOverviewText(HintContext.CaseInfo caseInfo) {
        List<String> lineList = new ArrayList<>();
        addLine(lineList, "제목", caseInfo.title());
        addLine(lineList, "사건 소개", caseInfo.introduction());
        addLine(lineList, "수사 목표", caseInfo.investigationGoal());
        addLine(lineList, "배경 지식", caseInfo.backgroundKnowledge());
        return lineList.isEmpty() ? NONE : String.join("\n", lineList);
    }

    private static String suspectsText(List<HintContext.SuspectInfo> suspectList) {
        return bulletsOrNone(suspectList.stream()
                .map(suspect -> isBlank(suspect.publicIntro())
                        ? suspect.name()
                        : suspect.name() + ": " + suspect.publicIntro())
                .toList());
    }

    private static String evidencesText(List<HintContext.EvidenceInfo> evidenceList) {
        return bulletsOrNone(evidenceList.stream()
                .map(evidence -> isBlank(evidence.description())
                        ? evidence.name()
                        : evidence.name() + ": " + evidence.description())
                .toList());
    }

    private static String solutionText(HintContext.CaseInfo caseInfo) {
        List<String> lineList = new ArrayList<>();
        addLine(lineList, "범인", caseInfo.culpritName());
        addLine(lineList, "범행 방법 요약", caseInfo.trickSummary());
        if (!caseInfo.trickElementList().isEmpty()) {
            lineList.add("범행 방법의 핵심 요소:");
            caseInfo.trickElementList().forEach(element -> lineList.add("- " + element));
        }
        return lineList.isEmpty() ? NONE : String.join("\n", lineList);
    }

    // 인물별로 묶고, 발화마다 출처(플레이어의 질문 / ○○의 진술)를 붙인다
    private static String conversationsText(List<HintContext.NpcConversation> conversationList) {
        if (conversationList.isEmpty()) {
            return NONE;
        }
        return conversationList.stream()
                .map(conversation -> "#### 대화 상대: " + conversation.suspectName() + "\n"
                        + conversation.messageList().stream()
                                .map(message -> "- " + (message.fromPlayer()
                                        ? "플레이어의 질문: "
                                        : conversation.suspectName() + "의 진술: ") + message.content())
                                .collect(Collectors.joining("\n")))
                .collect(Collectors.joining("\n\n"));
    }

    private static String previousHintsText(List<HintContext.PreviousHint> previousHintList) {
        if (previousHintList.isEmpty()) {
            return NONE;
        }
        List<String> blockList = new ArrayList<>();
        for (int i = 0; i < previousHintList.size(); i++) {
            HintContext.PreviousHint previousHint = previousHintList.get(i);
            blockList.add((i + 1) + ". 질문: " + previousHint.question() + "\n   답변: " + previousHint.content());
        }
        return String.join("\n", blockList);
    }

    private static String bulletsOrNone(List<String> itemList) {
        if (itemList.isEmpty()) {
            return NONE;
        }
        return itemList.stream()
                .map(item -> "- " + item)
                .collect(Collectors.joining("\n"));
    }

    private static void addLine(List<String> lineList, String label, String value) {
        if (!isBlank(value)) {
            lineList.add(label + ": " + value);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
