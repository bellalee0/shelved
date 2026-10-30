package com.shelved.common.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 설정값 (shelved.jwt)
 * 서명 키 길이 검증은 JwtProvider 생성 시 한다. 바인딩 실패 메시지에 키 값이 출력되지 않게 하기 위함이다.
 *
 * @param secretKey       액세스 토큰 서명 키 (환경 변수 JWT_SECRET_KEY, UTF-8 32바이트 이상)
 * @param accessTokenTtl  액세스 토큰 유효기간
 * @param refreshTokenTtl 리프레시 토큰 유효기간
 */
@ConfigurationProperties(prefix = "shelved.jwt")
public record JwtProperties(
        String secretKey,
        Duration accessTokenTtl,
        Duration refreshTokenTtl
) {
}
