package com.shelved.domain.accusation.service;

import com.shelved.common.config.GameProperties;
import com.shelved.common.config.GradingProperties;
import com.shelved.common.enums.Verdict;
import com.shelved.infra.llm.LlmRequest;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 지목 채점의 순수 로직 진입점. AccusationHandler(BE-11b)가 AiRequestHandler 단계마다 호출한다.
 *
 * <ol>
 *     <li>prepare(접수 트랜잭션): GradingContextLoader.load(room, suspectId, trickExplanation)</li>
 *     <li>buildLlmRequest(트랜잭션 밖): buildLlmRequest(context)</li>
 *     <li>complete(확정 트랜잭션): grade(context, response.text()) → Accusation.completeGrading(...)에 반영.
 *     형식 오류면 GradingResponseInvalidException(AiResponseInvalidException)이 나가 채점 실패로 처리된다</li>
 * </ol>
 * DB를 읽지 않는다
 */
@Component
public class AccusationGrader {

    private final GradingPromptBuilder gradingPromptBuilder;
    private final GradingResponseParser gradingResponseParser;
    private final GradingProperties gradingProperties;
    private final GameProperties gameProperties;

    public AccusationGrader(
            GradingPromptBuilder gradingPromptBuilder,
            GradingProperties gradingProperties,
            GameProperties gameProperties,
            JsonMapper jsonMapper
    ) {
        this.gradingPromptBuilder = gradingPromptBuilder;
        this.gradingProperties = gradingProperties;
        this.gameProperties = gameProperties;
        this.gradingResponseParser = new GradingResponseParser(jsonMapper);
    }

    public LlmRequest buildLlmRequest(GradingContext context) {
        return gradingPromptBuilder.build(context);
    }

    /**
     * 응답을 검증하고 최종 판정을 계산한다
     *
     * @throws GradingResponseInvalidException 응답 형식 오류 (채점 실패)
     */
    public GradingResult grade(GradingContext context, String responseText) {

        GradingResponseParser.ParsedGrading parsed = gradingResponseParser.parse(
                responseText, context.expectedItemKeyList(), gradingProperties.feedbackMaxLength());

        Verdict verdict = VerdictCalculator.calculate(
                context.culpritCorrect(),
                parsed.elementResultList(),
                gameProperties.grading().elementPassRatio());

        return new GradingResult(context.culpritCorrect(), verdict, parsed.elementResultList(), parsed.feedback());
    }
}
