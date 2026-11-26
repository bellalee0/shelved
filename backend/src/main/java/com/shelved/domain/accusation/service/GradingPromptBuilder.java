package com.shelved.domain.accusation.service;

import com.shelved.common.config.GradingProperties;
import com.shelved.common.enums.Difficulty;
import com.shelved.common.enums.LlmRequestType;
import com.shelved.common.enums.RubricItemResult;
import com.shelved.infra.llm.GlossaryPromptFormatter;
import com.shelved.infra.llm.LlmMessage;
import com.shelved.infra.llm.LlmModelTier;
import com.shelved.infra.llm.LlmRequest;
import com.shelved.infra.llm.PromptTemplateLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 지목 채점 요청(LlmRequest) 구성. DB를 읽지 않고 컨텍스트만 쓴다 (트랜잭션 밖에서 호출).
 *
 * <ul>
 *     <li>시스템 프롬프트 = grading-system.txt (사건과 무관한 판정·피드백 규칙) + 난이도별 피드백 방식 조각 + 지목 결과(범인 여부만)
 *     + 용어집 + 채점 기준(요소·항목·예시, 쉬움일 때만 쉬움 피드백 참고)</li>
 *     <li>메시지 = 답안 1개. grading-answer.txt의 &lt;submitted_answer&gt; 구분자로 감싸고, 답안 안의 같은 태그는 무력화한다</li>
 *     <li>지목한 인물의 이름·범인 이름·인물 ID는 넣지 않는다. 범인 일치 여부(boolean)만 피드백 작성용으로 넣는다</li>
 *     <li>이전 제출·NPC 대화·힌트·노트·수집한 단서는 넣지 않는다 (제출한 답안만 평가)</li>
 *     <li>요청 유형 GRADING, 모델 MAIN, 구조화 출력(OUTPUT_SCHEMA), 생각·effort·출력 토큰은 shelved.llm.grading 설정값</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class GradingPromptBuilder {

    static final String SYSTEM_TEMPLATE_NAME = "grading-system.txt";
    static final String ANSWER_TEMPLATE_NAME = "grading-answer.txt";
    static final String CULPRIT_CORRECT_TEXT = "범인이 맞음 (지목한 인물은 범인이다)";
    static final String CULPRIT_WRONG_TEXT = "범인이 아님 (지목한 인물은 범인이 아니다)";
    static final String NONE = "(없음)";
    // 답안이 구분자를 닫고 지시를 이어 쓰지 못하게, 답안 안의 여닫는 태그를 다른 표기로 바꾼다
    private static final Pattern ANSWER_TAG_PATTERN = Pattern.compile("(?i)<\\s*/?\\s*submitted_answer\\s*>");
    private static final String ANSWER_TAG_REPLACEMENT = "[submitted_answer]";

    /**
     * 채점 응답 스키마. 필드는 ElementResult(trickElementId, itemId, result, evidence, reason)로 그대로 옮긴다.
     * 모든 필드 required, 모든 object additionalProperties: false (구조화 출력의 필수 조건). 길이 제약은 지원되지 않아 서버에서 검사한다.
     * 판정 뒤에 피드백을 쓰도록 items를 feedback보다 앞에 둔다
     */
    public static final Map<String, Object> OUTPUT_SCHEMA = outputSchema();

    private final PromptTemplateLoader promptTemplateLoader;
    private final GradingProperties gradingProperties;

    public LlmRequest build(GradingContext context) {

        String systemPrompt = promptTemplateLoader.render(SYSTEM_TEMPLATE_NAME, Map.of(
                "difficultyGuide", promptTemplateLoader.load(difficultyTemplateName(context.difficulty())).strip(),
                "culpritStatus", context.culpritCorrect() ? CULPRIT_CORRECT_TEXT : CULPRIT_WRONG_TEXT,
                "glossary", GlossaryPromptFormatter.format(context.glossary()),
                "elements", elementsText(context)
        ));
        String answerMessage = promptTemplateLoader.render(ANSWER_TEMPLATE_NAME, Map.of(
                "trickExplanation", neutralizeAnswerTag(context.trickExplanation())
        ));

        return new LlmRequest(
                LlmRequestType.GRADING,
                LlmModelTier.MAIN,
                systemPrompt,
                List.of(LlmMessage.user(answerMessage)),
                gradingProperties.maxOutputTokens()
        ).withOutputSchema(OUTPUT_SCHEMA).withThinking(gradingProperties.thinking(), gradingProperties.effort());
    }

    static String difficultyTemplateName(Difficulty difficulty) {
        return switch (difficulty) {
            case EASY -> "grading-feedback-easy.txt";
            case NORMAL -> "grading-feedback-normal.txt";
            case HARD -> "grading-feedback-hard.txt";
        };
    }

    static String neutralizeAnswerTag(String answer) {
        return ANSWER_TAG_PATTERN.matcher(answer).replaceAll(ANSWER_TAG_REPLACEMENT);
    }

    private static String elementsText(GradingContext context) {
        List<String> blockList = new ArrayList<>();
        for (int i = 0; i < context.elementList().size(); i++) {
            GradingContext.Element element = context.elementList().get(i);
            List<String> lineList = new ArrayList<>();
            lineList.add("### 요소 " + (i + 1) + " (trickElementId: " + element.trickElementId() + ")");
            lineList.add("요소 설명: " + textOrNone(element.description()));
            lineList.add("필수 판정 항목:");
            for (GradingContext.Item item : element.itemList()) {
                lineList.add("- itemId: " + item.itemId() + " / 항목: " + textOrNone(item.label()));
                lineList.add("  기준: " + textOrNone(item.criterion()));
                lineList.add("  인정 예시: " + joinedOrNone(item.acceptExampleList()));
                lineList.add("  불인정 예시: " + joinedOrNone(item.rejectExampleList()));
            }
            if (context.difficulty() == Difficulty.EASY && element.easyGuide() != null) {
                GradingContext.EasyGuide guide = element.easyGuide();
                lineList.add("[쉬움 피드백 참고] (이 요소의 항목이 MET이 아닐 때만 활용한다)");
                lineList.add("- 보완 질문: " + textOrNone(guide.guideQuestion()));
                lineList.add("- 참고할 물증: " + joinedOrNone(guide.evidenceNameList()));
                lineList.add("- 참고할 인물: " + joinedOrNone(guide.suspectNameList()));
            }
            blockList.add(String.join("\n", lineList));
        }
        return String.join("\n\n", blockList);
    }

    private static String textOrNone(String value) {
        return value == null || value.isBlank() ? NONE : value.strip();
    }

    private static String joinedOrNone(List<String> valueList) {
        List<String> nonBlankList = valueList.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::strip)
                .toList();
        return nonBlankList.isEmpty() ? NONE : String.join("; ", nonBlankList);
    }

    private static Map<String, Object> outputSchema() {
        Map<String, Object> itemProperties = new LinkedHashMap<>();
        itemProperties.put("trickElementId", Map.of("type", "integer"));
        itemProperties.put("itemId", Map.of("type", "string"));
        itemProperties.put("result", Map.of(
                "type", "string",
                "enum", Arrays.stream(RubricItemResult.values()).map(Enum::name).toList()));
        itemProperties.put("evidence", Map.of("type", "string"));
        itemProperties.put("reason", Map.of("type", "string"));

        Map<String, Object> itemSchema = new LinkedHashMap<>();
        itemSchema.put("type", "object");
        itemSchema.put("properties", Collections.unmodifiableMap(itemProperties));
        itemSchema.put("required", List.copyOf(itemProperties.keySet()));
        itemSchema.put("additionalProperties", false);

        Map<String, Object> rootProperties = new LinkedHashMap<>();
        rootProperties.put("items", Map.of("type", "array", "items", Collections.unmodifiableMap(itemSchema)));
        rootProperties.put("feedback", Map.of("type", "string"));

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("type", "object");
        root.put("properties", Collections.unmodifiableMap(rootProperties));
        root.put("required", List.copyOf(rootProperties.keySet()));
        root.put("additionalProperties", false);
        return Collections.unmodifiableMap(root);
    }
}
