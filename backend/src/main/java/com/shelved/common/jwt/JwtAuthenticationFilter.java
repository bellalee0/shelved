package com.shelved.common.jwt;

import static com.shelved.common.exception.ErrorCode.INVALID_TOKEN;

import com.shelved.common.entity.User;
import com.shelved.common.exception.CustomException;
import com.shelved.common.exception.ErrorCode;
import com.shelved.domain.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authorization: Bearer {accessToken} 헤더로 사용자를 인증한다.
 * 토큰 오류는 여기서 바로 응답하지 않고 요청 속성에 기록한다. 인증이 필요한 경로라면 AuthenticationEntryPoint가 그 코드로 응답하고,
 * 인증 불필요 경로(SecurityPaths.PUBLIC)는 토큰이 잘못되어도 그대로 통과한다.
 * Spring Boot가 서블릿 필터로 중복 등록하지 않도록 빈으로 만들지 않고 SecurityConfig에서 생성한다.
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String AUTH_ERROR_ATTRIBUTE = JwtAuthenticationFilter.class.getName() + ".AUTH_ERROR";

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authorization != null) {
            try {
                authenticate(authorization);
            } catch (CustomException e) {
                request.setAttribute(AUTH_ERROR_ATTRIBUTE, e.getErrorCode());
            }
        }

        filterChain.doFilter(request, response);
    }

    public static ErrorCode resolveAuthError(HttpServletRequest request) {
        return request.getAttribute(AUTH_ERROR_ATTRIBUTE) instanceof ErrorCode errorCode
                ? errorCode
                : ErrorCode.LOGIN_REQUIRED;
    }

    private void authenticate(String authorization) {

        if (!authorization.startsWith(BEARER_PREFIX)) {
            throw new CustomException(INVALID_TOKEN);
        }

        Long userId = jwtProvider.getUserId(authorization.substring(BEARER_PREFIX.length()).trim());
        // 토큰 발급 후 삭제된 사용자
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(INVALID_TOKEN));
        AuthUser authUser = AuthUser.from(user);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(authUser, null, authUser.getAuthorities()));
        SecurityContextHolder.setContext(context);
    }
}
