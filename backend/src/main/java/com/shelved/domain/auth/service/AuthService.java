package com.shelved.domain.auth.service;

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
import com.shelved.domain.user.dto.UserMeResponse;
import com.shelved.domain.user.repository.UserRepository;
import com.shelved.infra.google.GoogleIdTokenVerifier;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final GoogleIdTokenVerifier googleIdTokenVerifier;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProvider jwtProvider;
    private final RefreshTokenProvider refreshTokenProvider;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    /**
     * Google ID 토큰으로 로그인. 처음 로그인한 사용자는 PLAYER, 닉네임 없이 생성한다.
     * Google 검증(외부 호출)을 트랜잭션 밖에서 하고, 사용자 생성 충돌을 처리하기 위해 메서드 전체를 한 트랜잭션으로 묶지 않는다.
     */
    public AuthTokenResponse loginWithGoogle(AuthGoogleLoginRequest request) {

        String googleSub = googleIdTokenVerifier.verify(request.idToken());

        User user = userRepository.findByGoogleSub(googleSub)
                .orElseGet(() -> createUser(googleSub));

        return issueTokens(user, LocalDateTime.now(clock));
    }

    /**
     * 리프레시 토큰 재발급(rotation). 이전 토큰 무효화와 새 토큰 저장을 한 트랜잭션에서 처리한다.
     */
    @Transactional
    public AuthTokenResponse reissueToken(AuthRefreshRequest request) {

        String tokenHash = refreshTokenProvider.hash(request.refreshToken());
        LocalDateTime now = LocalDateTime.now(clock);

        // 조건부 갱신으로 같은 토큰의 중복 rotation을 막는다. 없음·만료·이미 무효화된 토큰은 0
        int revokedCount = refreshTokenRepository.revokeActiveToken(tokenHash, now);
        if (revokedCount == 0) {
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        RefreshToken refreshToken = refreshTokenRepository.findRefreshTokenWithUser(tokenHash);

        return issueTokens(refreshToken.getUser(), now);
    }

    /**
     * 리프레시 토큰 무효화. 이미 무효이거나 없는 토큰이어도 성공으로 처리한다.
     */
    @Transactional
    public void logout(AuthLogoutRequest request) {

        String tokenHash = refreshTokenProvider.hash(request.refreshToken());

        refreshTokenRepository.revokeActiveToken(tokenHash, LocalDateTime.now(clock));
    }

    // 같은 사용자가 처음 로그인하며 동시에 요청하면 google_sub UNIQUE 위반이 나므로, 먼저 생성된 사용자를 다시 조회한다
    private User createUser(String googleSub) {
        try {
            return userRepository.saveAndFlush(new User(googleSub, UserRole.PLAYER));
        } catch (DataIntegrityViolationException e) {
            return userRepository.findUserByGoogleSub(googleSub);
        }
    }

    private AuthTokenResponse issueTokens(User user, LocalDateTime now) {

        String accessToken = jwtProvider.createAccessToken(user.getId());
        String refreshToken = refreshTokenProvider.generate();

        LocalDateTime refreshTokenExpiresAt = now.plus(jwtProperties.refreshTokenTtl());
        refreshTokenRepository.save(new RefreshToken(user, refreshTokenProvider.hash(refreshToken), refreshTokenExpiresAt));

        return new AuthTokenResponse(
                accessToken,
                refreshToken,
                jwtProvider.getAccessTokenExpiresInSec(),
                UserMeResponse.from(user)
        );
    }
}
