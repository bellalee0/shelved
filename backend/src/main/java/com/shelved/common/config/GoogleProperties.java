package com.shelved.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Google 로그인 설정값 (shelved.google)
 *
 * @param clientId ID 토큰의 대상(aud) 검증에 쓰는 OAuth 클라이언트 ID (환경 변수 GOOGLE_CLIENT_ID). 비어 있으면 로그인 요청이 모두 실패한다
 */
@ConfigurationProperties(prefix = "shelved.google")
public record GoogleProperties(
        String clientId
) {
}
