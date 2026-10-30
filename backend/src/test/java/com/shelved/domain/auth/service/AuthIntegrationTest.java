package com.shelved.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shelved.IntegrationTestSupport;
import com.shelved.common.entity.RefreshToken;
import com.shelved.common.entity.User;
import com.shelved.common.enums.UserRole;
import com.shelved.common.exception.CustomException;
import com.shelved.common.exception.ErrorCode;
import com.shelved.common.jwt.RefreshTokenProvider;
import com.shelved.domain.auth.dto.AuthLogoutRequest;
import com.shelved.domain.auth.dto.AuthRefreshRequest;
import com.shelved.domain.auth.dto.AuthTokenResponse;
import com.shelved.domain.auth.repository.RefreshTokenRepository;
import com.shelved.domain.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class AuthIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private RefreshTokenProvider refreshTokenProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void reissueToken_같은토큰동시요청_한번만성공() throws Exception {

        // given
        User user = saveUser();
        String refreshToken = saveRefreshToken(user, LocalDateTime.now().plusDays(1));
        int threadCount = 5;
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<ErrorCode>> tasks = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            tasks.add(() -> {
                ready.countDown();
                start.await();
                try {
                    authService.reissueToken(new AuthRefreshRequest(refreshToken));
                    return null;
                } catch (CustomException e) {
                    return e.getErrorCode();
                }
            });
        }

        // when
        List<ErrorCode> results = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            List<Future<ErrorCode>> futures = tasks.stream().map(executor::submit).toList();
            ready.await();
            start.countDown();
            for (Future<ErrorCode> future : futures) {
                results.add(future.get());
            }
        }

        // then
        assertThat(results).filteredOn(code -> code == null).hasSize(1);
        assertThat(results).filteredOn(code -> code != null)
                .hasSize(threadCount - 1)
                .containsOnly(ErrorCode.INVALID_REFRESH_TOKEN);
        assertThat(countActiveTokens(user)).isEqualTo(1);
    }

    @Test
    void reissueToken_rotation후이전토큰_예외() {

        // given
        User user = saveUser();
        String oldToken = saveRefreshToken(user, LocalDateTime.now().plusDays(1));
        AuthTokenResponse reissued = authService.reissueToken(new AuthRefreshRequest(oldToken));

        // when & then
        assertThatThrownBy(() -> authService.reissueToken(new AuthRefreshRequest(oldToken)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
        assertThat(authService.reissueToken(new AuthRefreshRequest(reissued.refreshToken())).user().userId())
                .isEqualTo(user.getId());
    }

    @Test
    void reissueToken_로그아웃후_예외() {

        // given
        User user = saveUser();
        String refreshToken = saveRefreshToken(user, LocalDateTime.now().plusDays(1));
        authService.logout(new AuthLogoutRequest(refreshToken));

        // when & then
        assertThatThrownBy(() -> authService.reissueToken(new AuthRefreshRequest(refreshToken)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
        assertThat(countActiveTokens(user)).isZero();
    }

    @Test
    void reissueToken_만료된토큰_예외() {

        // given
        User user = saveUser();
        String refreshToken = saveRefreshToken(user, LocalDateTime.now().minusSeconds(1));

        // when & then
        assertThatThrownBy(() -> authService.reissueToken(new AuthRefreshRequest(refreshToken)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    void logout_이미로그아웃한토큰_성공() {

        // given
        User user = saveUser();
        String refreshToken = saveRefreshToken(user, LocalDateTime.now().plusDays(1));
        authService.logout(new AuthLogoutRequest(refreshToken));

        // when & then: 예외 없이 끝난다
        authService.logout(new AuthLogoutRequest(refreshToken));
    }

    private User saveUser() {
        return userRepository.save(new User("google-" + UUID.randomUUID(), UserRole.PLAYER));
    }

    private String saveRefreshToken(User user, LocalDateTime expiresAt) {
        String refreshToken = refreshTokenProvider.generate();
        refreshTokenRepository.save(new RefreshToken(user, refreshTokenProvider.hash(refreshToken), expiresAt));
        return refreshToken;
    }

    private int countActiveTokens(User user) {
        return jdbcTemplate.queryForObject(
                "select count(*) from refresh_tokens where user_id = ? and revoked_at is null",
                Integer.class, user.getId());
    }
}
