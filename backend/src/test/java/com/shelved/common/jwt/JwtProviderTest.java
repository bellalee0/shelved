package com.shelved.common.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shelved.common.config.JwtProperties;
import com.shelved.common.exception.CustomException;
import com.shelved.common.exception.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class JwtProviderTest {

    private static final String SECRET_KEY = "test-only-fake-jwt-secret-key-0123456789";
    private static final Instant NOW = Instant.parse("2026-10-30T03:00:00Z");

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"short-key-under-32-bytes"})
    void 생성_서명키없음또는짧음_기동실패(String secretKey) {

        // given
        JwtProperties properties = properties(secretKey);
        Clock clock = clockAt(NOW);

        // when & then: 메시지에 키 값을 넣지 않는다
        assertThatThrownBy(() -> new JwtProvider(properties, clock))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET_KEY")
                .satisfies(e -> {
                    if (secretKey != null && !secretKey.isEmpty()) {
                        assertThat(e.getMessage()).doesNotContain(secretKey);
                    }
                });
    }

    @Test
    void getUserId_유효토큰_사용자ID() {

        // given
        JwtProvider jwtProvider = new JwtProvider(properties(SECRET_KEY), clockAt(NOW));
        String token = jwtProvider.createAccessToken(7L);

        // when
        Long userId = jwtProvider.getUserId(token);

        // then
        assertThat(userId).isEqualTo(7L);
        assertThat(jwtProvider.getAccessTokenExpiresInSec()).isEqualTo(3600);
    }

    @Test
    void getUserId_만료시각도달_EXPIRED_TOKEN() {

        // given
        String token = new JwtProvider(properties(SECRET_KEY), clockAt(NOW)).createAccessToken(7L);
        JwtProvider oneHourLater = new JwtProvider(properties(SECRET_KEY), clockAt(NOW.plus(Duration.ofHours(1))));

        // when & then
        assertThatThrownBy(() -> oneHourLater.getUserId(token))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.EXPIRED_TOKEN);
    }

    @Test
    void getUserId_서명변조_INVALID_TOKEN() {

        // given
        JwtProvider jwtProvider = new JwtProvider(properties(SECRET_KEY), clockAt(NOW));
        String token = jwtProvider.createAccessToken(7L);
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("AA") ? "BB" : "AA");

        // when & then
        assertThatThrownBy(() -> jwtProvider.getUserId(tampered))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    private static JwtProperties properties(String secretKey) {
        return new JwtProperties(secretKey, Duration.ofHours(1), Duration.ofDays(14));
    }

    private static Clock clockAt(Instant instant) {
        return Clock.fixed(instant, ZoneId.of("Asia/Seoul"));
    }
}
