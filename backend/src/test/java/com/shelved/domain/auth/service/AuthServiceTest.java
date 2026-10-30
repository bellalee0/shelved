package com.shelved.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.shelved.common.config.JwtProperties;
import com.shelved.common.entity.RefreshToken;
import com.shelved.common.entity.User;
import com.shelved.common.enums.UserRole;
import com.shelved.common.exception.CustomException;
import com.shelved.common.exception.ErrorCode;
import com.shelved.common.jwt.JwtProvider;
import com.shelved.common.jwt.RefreshTokenProvider;
import com.shelved.domain.auth.dto.AuthGoogleLoginRequest;
import com.shelved.domain.auth.dto.AuthLogoutRequest;
import com.shelved.domain.auth.dto.AuthRefreshRequest;
import com.shelved.domain.auth.dto.AuthTokenResponse;
import com.shelved.domain.auth.repository.RefreshTokenRepository;
import com.shelved.domain.user.repository.UserRepository;
import com.shelved.infra.google.GoogleIdTokenVerifier;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-30T03:00:00Z"), ZONE);
    private static final JwtProperties JWT_PROPERTIES = new JwtProperties(
            "test-only-fake-jwt-secret-key-0123456789", Duration.ofHours(1), Duration.ofDays(14));

    @Mock
    private GoogleIdTokenVerifier googleIdTokenVerifier;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private final JwtProvider jwtProvider = new JwtProvider(JWT_PROPERTIES, CLOCK);
    private final RefreshTokenProvider refreshTokenProvider = new RefreshTokenProvider();

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(googleIdTokenVerifier, userRepository, refreshTokenRepository,
                jwtProvider, refreshTokenProvider, JWT_PROPERTIES, CLOCK);
    }

    @Test
    void loginWithGoogle_첫로그인_사용자생성() {

        // given
        given(googleIdTokenVerifier.verify("id-token")).willReturn("google-sub-1");
        given(userRepository.findByGoogleSub("google-sub-1")).willReturn(Optional.empty());
        given(userRepository.saveAndFlush(any(User.class))).willAnswer(invocation -> withId(invocation.getArgument(0), 7L));

        // when
        AuthTokenResponse response = authService.loginWithGoogle(new AuthGoogleLoginRequest("id-token"));

        // then
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());
        assertThat(userCaptor.getValue().getGoogleSub()).isEqualTo("google-sub-1");
        assertThat(userCaptor.getValue().getRole()).isEqualTo(UserRole.PLAYER);

        assertThat(response.user().userId()).isEqualTo(7L);
        assertThat(response.user().nickname()).isNull();
        assertThat(response.user().nicknameRequired()).isTrue();
        assertThat(response.accessTokenExpiresInSec()).isEqualTo(3600);
        assertThat(jwtProvider.getUserId(response.accessToken())).isEqualTo(7L);
        assertSavedRefreshToken(response.refreshToken());
    }

    @Test
    void loginWithGoogle_동시첫로그인으로UNIQUE위반_기존사용자로로그인() {

        // given
        User existing = withId(new User("google-sub-1", UserRole.PLAYER), 7L);
        given(googleIdTokenVerifier.verify("id-token")).willReturn("google-sub-1");
        given(userRepository.findByGoogleSub("google-sub-1")).willReturn(Optional.empty());
        given(userRepository.saveAndFlush(any(User.class))).willThrow(new DataIntegrityViolationException("uk_users_google_sub"));
        given(userRepository.findUserByGoogleSub("google-sub-1")).willReturn(existing);

        // when
        AuthTokenResponse response = authService.loginWithGoogle(new AuthGoogleLoginRequest("id-token"));

        // then
        assertThat(response.user().userId()).isEqualTo(7L);
    }

    @Test
    void loginWithGoogle_기존사용자_로그인() {

        // given
        User user = withId(new User("google-sub-1", UserRole.ADMIN), 3L);
        user.updateNickname("Detective");
        given(googleIdTokenVerifier.verify("id-token")).willReturn("google-sub-1");
        given(userRepository.findByGoogleSub("google-sub-1")).willReturn(Optional.of(user));

        // when
        AuthTokenResponse response = authService.loginWithGoogle(new AuthGoogleLoginRequest("id-token"));

        // then
        verify(userRepository, never()).saveAndFlush(any());
        assertThat(response.user().userId()).isEqualTo(3L);
        assertThat(response.user().nickname()).isEqualTo("Detective");
        assertThat(response.user().role()).isEqualTo(UserRole.ADMIN);
        assertThat(response.user().nicknameRequired()).isFalse();
    }

    @Test
    void loginWithGoogle_Google토큰검증실패_예외() {

        // given
        given(googleIdTokenVerifier.verify("forged")).willThrow(new CustomException(ErrorCode.GOOGLE_TOKEN_INVALID));

        // when & then
        assertThatThrownBy(() -> authService.loginWithGoogle(new AuthGoogleLoginRequest("forged")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.GOOGLE_TOKEN_INVALID);
        verify(userRepository, never()).findByGoogleSub(any());
    }

    @Test
    void reissueToken_성공_새토큰발급() {

        // given
        String oldToken = "old-refresh-token";
        String oldHash = refreshTokenProvider.hash(oldToken);
        User user = withId(new User("google-sub-1", UserRole.PLAYER), 7L);
        RefreshToken stored = new RefreshToken(user, oldHash, now().plusDays(1));
        given(refreshTokenRepository.revokeActiveToken(oldHash, now())).willReturn(1);
        given(refreshTokenRepository.findRefreshTokenWithUser(oldHash)).willReturn(stored);

        // when
        AuthTokenResponse response = authService.reissueToken(new AuthRefreshRequest(oldToken));

        // then
        assertThat(response.refreshToken()).isNotEqualTo(oldToken);
        assertThat(jwtProvider.getUserId(response.accessToken())).isEqualTo(7L);
        assertSavedRefreshToken(response.refreshToken());
    }

    @Test
    void reissueToken_만료무효화없는토큰_예외() {

        // given: 조건부 무효화 쿼리가 대상 행을 찾지 못함 (만료·이미 무효화·존재하지 않음)
        String token = "revoked-or-expired";
        given(refreshTokenRepository.revokeActiveToken(eq(refreshTokenProvider.hash(token)), any())).willReturn(0);

        // when & then
        assertThatThrownBy(() -> authService.reissueToken(new AuthRefreshRequest(token)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void logout_성공_해시로무효화() {

        // given
        String token = "refresh-token";
        given(refreshTokenRepository.revokeActiveToken(refreshTokenProvider.hash(token), now())).willReturn(1);

        // when
        authService.logout(new AuthLogoutRequest(token));

        // then
        verify(refreshTokenRepository).revokeActiveToken(refreshTokenProvider.hash(token), now());
    }

    @Test
    void logout_없는토큰_성공() {

        // given
        String token = "unknown-token";
        given(refreshTokenRepository.revokeActiveToken(eq(refreshTokenProvider.hash(token)), any())).willReturn(0);

        // when & then: 예외 없이 끝난다
        authService.logout(new AuthLogoutRequest(token));
    }

    private void assertSavedRefreshToken(String rawToken) {

        ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(tokenCaptor.capture());
        RefreshToken saved = tokenCaptor.getValue();

        // 원문이 아닌 해시만 저장한다
        assertThat(saved.getTokenHash()).isEqualTo(refreshTokenProvider.hash(rawToken));
        assertThat(saved.getTokenHash()).isNotEqualTo(rawToken);
        assertThat(saved.getExpiresAt()).isEqualTo(now().plusDays(14));
        assertThat(saved.getRevokedAt()).isNull();
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(CLOCK);
    }

    private static User withId(User user, Long id) {
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
