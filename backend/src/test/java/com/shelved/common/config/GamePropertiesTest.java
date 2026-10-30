package com.shelved.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.shelved.common.enums.Difficulty;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class GamePropertiesTest {

    private final GameProperties gameProperties = new GameProperties(
            new GameProperties.TimeLimit(Duration.ofMinutes(20), Duration.ofMinutes(25), Duration.ofMinutes(35)),
            new GameProperties.HintLimit(5, 3, 1),
            3,
            Duration.ofSeconds(5),
            Duration.ofSeconds(15),
            Duration.ofSeconds(10)
    );

    @Test
    void timeLimitOf_난이도별_설정된제한시간반환() {

        // given & when & then
        assertThat(gameProperties.timeLimitOf(Difficulty.EASY)).isEqualTo(Duration.ofMinutes(20));
        assertThat(gameProperties.timeLimitOf(Difficulty.NORMAL)).isEqualTo(Duration.ofMinutes(25));
        assertThat(gameProperties.timeLimitOf(Difficulty.HARD)).isEqualTo(Duration.ofMinutes(35));
    }

    @Test
    void hintLimitOf_난이도별_설정된힌트횟수반환() {

        // given & when & then
        assertThat(gameProperties.hintLimitOf(Difficulty.EASY)).isEqualTo(5);
        assertThat(gameProperties.hintLimitOf(Difficulty.NORMAL)).isEqualTo(3);
        assertThat(gameProperties.hintLimitOf(Difficulty.HARD)).isEqualTo(1);
    }
}
