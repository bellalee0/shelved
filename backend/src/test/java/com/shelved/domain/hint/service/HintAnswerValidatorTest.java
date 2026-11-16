package com.shelved.domain.hint.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shelved.domain.airequest.service.AiResponseInvalidException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 힌트·보충 질문 답변의 형식 검증
 */
class HintAnswerValidatorTest {

    @Test
    void validate_정상답변_앞뒤공백제거_상한허용() {

        // when
        String trimmed = HintAnswerValidator.validate("\n  시각을 다시 확인해 보세요.  ", "힌트");
        String longest = HintAnswerValidator.validate("가".repeat(HintAnswerValidator.ANSWER_MAX_LENGTH), "힌트");

        // then
        assertThat(trimmed).isEqualTo("시각을 다시 확인해 보세요.");
        assertThat(longest).hasSize(HintAnswerValidator.ANSWER_MAX_LENGTH);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\n\t "})
    void validate_빈답변_형식오류(String text) {

        // when & then
        assertThatThrownBy(() -> HintAnswerValidator.validate(text, "힌트"))
                .isInstanceOf(AiResponseInvalidException.class);
    }

    @Test
    void validate_과도하게긴답변_형식오류_메시지에본문없음() {

        // given
        String text = "종".repeat(HintAnswerValidator.ANSWER_MAX_LENGTH + 1);

        // when & then
        assertThatThrownBy(() -> HintAnswerValidator.validate(text, "힌트"))
                .isInstanceOf(AiResponseInvalidException.class)
                .message().doesNotContain("종종");
    }
}
