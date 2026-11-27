package com.shelved.domain.accusation.service;

import static com.shelved.domain.accusation.service.GradingTestData.EXPECTED_KEYS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shelved.common.config.GradingProperties;
import com.shelved.common.enums.Difficulty;
import com.shelved.common.enums.Verdict;
import com.shelved.domain.airequest.service.AiResponseInvalidException;
import com.shelved.infra.llm.FakeLlmClient;
import com.shelved.infra.llm.FakeLlmScenario;
import com.shelved.infra.llm.LlmRequest;
import com.shelved.infra.llm.LlmResponse;
import com.shelved.infra.llm.LlmThinking;
import com.shelved.infra.llm.PromptTemplateLoader;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import tools.jackson.databind.json.JsonMapper;

/**
 * 요청 구성 → (Fake LLM) → 응답 검증 → 최종 판정. 실제 API는 호출하지 않는다
 */
class AccusationGraderTest {

    private final GradingProperties gradingProperties = new GradingProperties(LlmThinking.MODEL_DEFAULT, null, 4000, 1500);
    private final AccusationGrader grader = new AccusationGrader(
            new GradingPromptBuilder(new PromptTemplateLoader(new DefaultResourceLoader()), gradingProperties),
            gradingProperties,
            JsonMapper.builder().build());
    private final FakeLlmClient fakeLlmClient = new FakeLlmClient();

    @Test
    void grade_범인맞음전부MET_CORRECT_결과에항목과피드백() {

        // given
        GradingResult result = gradeWith(true, GradingFakeResponses.allMet(EXPECTED_KEYS));

        // then
        assertThat(result.culpritCorrect()).isTrue();
        assertThat(result.verdict()).isEqualTo(Verdict.CORRECT);
        assertThat(result.elementResultList()).hasSize(EXPECTED_KEYS.size());
        assertThat(result.feedback()).isEqualTo(GradingFakeResponses.FEEDBACK);
    }

    @Test
    void grade_범인맞음일부MET_PARTIAL() {
        assertThat(gradeWith(true, GradingFakeResponses.firstMet(EXPECTED_KEYS)).verdict()).isEqualTo(Verdict.PARTIAL);
    }

    @Test
    void grade_범인맞음MET없음_INCORRECT() {
        assertThat(gradeWith(true, GradingFakeResponses.allMissing(EXPECTED_KEYS)).verdict()).isEqualTo(Verdict.INCORRECT);
        assertThat(gradeWith(true, GradingFakeResponses.incorrectMixed(EXPECTED_KEYS)).verdict()).isEqualTo(Verdict.INCORRECT);
    }

    @Test
    void grade_범인틀림전부MET_INCORRECT() {

        // given
        GradingResult result = gradeWith(false, GradingFakeResponses.allMet(EXPECTED_KEYS));

        // then
        assertThat(result.culpritCorrect()).isFalse();
        assertThat(result.verdict()).isEqualTo(Verdict.INCORRECT);
    }

    @Test
    void grade_형식오류_오답이아닌AiResponseInvalidException() {

        // given
        GradingContext context = GradingTestData.context(Difficulty.EASY, true);
        fakeLlmClient.enqueue(FakeLlmScenario.respond(GradingFakeResponses.unknownItem(EXPECTED_KEYS)));
        LlmResponse response = fakeLlmClient.call(grader.buildLlmRequest(context));

        // when & then
        assertThatThrownBy(() -> grader.grade(context, response.text()))
                .isInstanceOf(AiResponseInvalidException.class)
                .hasMessage("GRADING_UNKNOWN_ITEM");
    }

    @Test
    void toString_컨텍스트요청결과에답안기준피드백없음() {

        // given
        GradingContext context = GradingTestData.context(Difficulty.EASY, true);
        LlmRequest request = grader.buildLlmRequest(context);
        GradingResult result = grader.grade(context, GradingFakeResponses.firstMet(EXPECTED_KEYS));

        // when
        String logged = context + " " + request + " " + result + " " + context.elementList() + " "
                + context.elementList().getFirst().itemList();

        // then
        assertThat(logged)
                .doesNotContain(GradingTestData.ANSWER, "녹음 장치를 사용했음을 밝힘", "녹음기", GradingFakeResponses.FEEDBACK)
                .doesNotContain(GradingTestData.ELEMENT_2_DESCRIPTION, GradingTestData.BUTLER_NAME, "판단 이유")
                .contains("GRADING", "structuredOutput=true", "verdict=PARTIAL");
    }

    private GradingResult gradeWith(boolean culpritCorrect, String responseText) {
        GradingContext context = GradingTestData.context(Difficulty.NORMAL, culpritCorrect);
        fakeLlmClient.enqueue(FakeLlmScenario.respond(responseText));
        LlmResponse response = fakeLlmClient.call(grader.buildLlmRequest(context));
        return grader.grade(context, response.text());
    }
}
