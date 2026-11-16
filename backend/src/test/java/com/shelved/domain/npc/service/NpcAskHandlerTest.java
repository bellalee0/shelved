package com.shelved.domain.npc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shelved.domain.airequest.service.AiResponseInvalidException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class NpcAskHandlerTest {

    @Test
    void validateAnswer_정상답변_앞뒤공백제거() {

        // given
        String text = "\n  그 시간이면 주방에서 은식기를 닦고 있었습니다.  \n";

        // when
        String answer = NpcAskHandler.validateAnswer(text);

        // then
        assertThat(answer).isEqualTo("그 시간이면 주방에서 은식기를 닦고 있었습니다.");
    }

    @Test
    void validateAnswer_상한길이_허용() {

        // given
        String text = "가".repeat(NpcAskHandler.ANSWER_MAX_LENGTH);

        // when
        String answer = NpcAskHandler.validateAnswer(text);

        // then
        assertThat(answer).hasSize(NpcAskHandler.ANSWER_MAX_LENGTH);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\n\t "})
    void validateAnswer_빈답변_형식오류(String text) {

        // given: text

        // when & then
        assertThatThrownBy(() -> NpcAskHandler.validateAnswer(text))
                .isInstanceOf(AiResponseInvalidException.class);
    }

    @Test
    void validateAnswer_과도하게긴답변_형식오류_메시지에본문없음() {

        // given
        String text = "종".repeat(NpcAskHandler.ANSWER_MAX_LENGTH + 1);

        // when & then
        assertThatThrownBy(() -> NpcAskHandler.validateAnswer(text))
                .isInstanceOf(AiResponseInvalidException.class)
                .message().doesNotContain("종종");
    }
}
