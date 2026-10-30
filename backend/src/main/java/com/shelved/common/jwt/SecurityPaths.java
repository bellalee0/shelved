package com.shelved.common.jwt;

import static org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher.pathPattern;

import org.springframework.http.HttpMethod;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * 인증·닉네임 검사 예외 경로
 * 인증이 필요 없는 API를 추가할 때는 PUBLIC에 추가하고 docs/api 명세의 '인증' 열과 맞춘다.
 */
public final class SecurityPaths {

    private SecurityPaths() {
    }

    // 인증 불필요. 토큰이 있어도 오류로 처리하지 않는다
    public static final RequestMatcher PUBLIC = new OrRequestMatcher(
            pathPattern(HttpMethod.POST, "/api/auth/google"),
            pathPattern(HttpMethod.POST, "/api/auth/refresh"),
            pathPattern(HttpMethod.POST, "/api/auth/logout"),
            pathPattern(HttpMethod.GET, "/api/cases"),
            pathPattern(HttpMethod.GET, "/uploads/covers/**"),
            // Swagger는 local 프로필에서만 실제로 열린다 (application-local.yml)
            pathPattern("/swagger-ui/**"),
            pathPattern("/swagger-ui.html"),
            pathPattern("/v3/api-docs/**")
    );

    // 닉네임이 없어도 호출할 수 있는 경로
    public static final RequestMatcher NICKNAME_OPTIONAL = new OrRequestMatcher(
            PUBLIC,
            pathPattern(HttpMethod.GET, "/api/users/me"),
            pathPattern(HttpMethod.PATCH, "/api/users/me/nickname")
    );

    // 관리자 전용
    public static final RequestMatcher ADMIN = pathPattern("/api/admin/**");
}
