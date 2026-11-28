package com.shelved.domain.accusation.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.shelved.common.config.GameProperties;
import com.shelved.common.config.GradingProperties;
import com.shelved.common.entity.json.ElementResult;
import com.shelved.common.enums.Difficulty;
import com.shelved.common.enums.RubricItemResult;
import com.shelved.common.enums.Verdict;
import com.shelved.infra.llm.FakeLlmClient;
import com.shelved.infra.llm.FakeLlmScenario;
import com.shelved.infra.llm.LlmRequest;
import com.shelved.infra.llm.LlmThinking;
import com.shelved.infra.llm.PromptTemplateLoader;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.core.io.DefaultResourceLoader;
import tools.jackson.databind.json.JsonMapper;

/**
 * FakeLlmClient의 채점 기본 응답이 GradingPromptBuilder의 채점 기준 블록 형식과 맞는지 고정한다.
 * 프롬프트 형식이 바뀌어 Fake가 항목을 읽지 못하면 여기서 실패한다
 */
class FakeGradingResponseTest {

    private final GradingProperties gradingProperties = new GradingProperties(LlmThinking.MODEL_DEFAULT, null, 4000, 1500);
    private final GradingPromptBuilder builder = new GradingPromptBuilder(
            new PromptTemplateLoader(new DefaultResourceLoader()), gradingProperties);
    private final AccusationGrader grader = new AccusationGrader(
            builder, gradingProperties, gameProperties(), JsonMapper.builder().build());
    private final FakeLlmClient fakeLlmClient = new FakeLlmClient();

    @ParameterizedTest
    @EnumSource(Difficulty.class)
    void call_채점요청기본응답_모든항목MET_범인맞음이면CORRECT(Difficulty difficulty) {

        // given
        GradingContext context = GradingTestData.context(difficulty, true);
        LlmRequest request = grader.buildLlmRequest(context);

        // when
        GradingResult result = grader.grade(context, fakeLlmClient.call(request).text());

        // then
        assertThat(result.verdict()).isEqualTo(Verdict.CORRECT);
        assertThat(result.elementResultList())
                .extracting(ElementResult::trickElementId, ElementResult::itemId)
                .containsExactlyElementsOf(GradingTestData.EXPECTED_KEYS.stream()
                        .map(key -> org.assertj.core.groups.Tuple.tuple(key.trickElementId(), key.itemId()))
                        .toList());
        assertThat(result.elementResultList()).extracting(ElementResult::result).containsOnly(RubricItemResult.MET);
        assertThat(result.feedback()).isEqualTo(FakeLlmClient.FAKE_GRADING_FEEDBACK);
    }

    @Test
    void call_채점요청기본응답_범인틀림이면INCORRECT() {

        // given
        GradingContext context = GradingTestData.context(Difficulty.NORMAL, false);

        // when
        GradingResult result = grader.grade(context, fakeLlmClient.call(grader.buildLlmRequest(context)).text());

        // then
        assertThat(result.verdict()).isEqualTo(Verdict.INCORRECT);
    }

    @Test
    void call_시나리오가있으면시나리오우선() {

        // given
        GradingContext context = GradingTestData.context(Difficulty.EASY, true);
        fakeLlmClient.enqueue(FakeLlmScenario.respond(GradingFakeResponses.firstMet(GradingTestData.EXPECTED_KEYS)));

        // when
        GradingResult result = grader.grade(context, fakeLlmClient.call(grader.buildLlmRequest(context)).text());

        // then
        assertThat(result.verdict()).isEqualTo(Verdict.PARTIAL);
    }

    // 판정 기준 비율만 쓰는 테스트용 게임 설정
    private static GameProperties gameProperties() {
        return new GameProperties(null, null, 3, null, null, null, null,
                new GameProperties.Grading(new BigDecimal("0.5")));
    }
}
