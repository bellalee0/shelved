package com.shelved.infra.llm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LlmTokenEstimatorTest {

    @Test
    void estimateText_영문은3글자당1토큰_한글은1글자당1토큰() {

        // given
        String english = "abcdef";
        String korean = "시계탑";

        // when
        long englishTokens = LlmTokenEstimator.estimateText(english);
        long koreanTokens = LlmTokenEstimator.estimateText(korean);

        // then
        assertThat(englishTokens).isEqualTo(2);
        assertThat(koreanTokens).isEqualTo(3);
        assertThat(LlmTokenEstimator.estimateText(null)).isZero();
    }
}
