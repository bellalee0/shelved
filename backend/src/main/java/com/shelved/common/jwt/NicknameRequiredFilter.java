package com.shelved.common.jwt;

import com.shelved.common.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 닉네임을 설정하지 않은 사용자는 로그인·재발급·로그아웃·내 정보·닉네임 설정 외의 API에서 403 NICKNAME_REQUIRED
 * 인가(AuthorizationFilter) 다음에 실행되므로 LOGIN_REQUIRED·ACCESS_DENIED가 먼저 판단된다.
 */
@RequiredArgsConstructor
public class NicknameRequiredFilter extends OncePerRequestFilter {

    private final SecurityErrorResponder securityErrorResponder;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null
                && authentication.getPrincipal() instanceof AuthUser authUser
                && authUser.isNicknameRequired()
                && !SecurityPaths.NICKNAME_OPTIONAL.matches(request)) {
            securityErrorResponder.write(response, ErrorCode.NICKNAME_REQUIRED);
            return;
        }

        filterChain.doFilter(request, response);
    }
}
