package com.shelved.common.jwt;

import static com.shelved.common.exception.ErrorCode.EXPIRED_TOKEN;
import static com.shelved.common.exception.ErrorCode.INVALID_TOKEN;

import com.shelved.common.config.JwtProperties;
import com.shelved.common.exception.CustomException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

/**
 * 액세스 토큰(HS256 JWT) 발급·검증
 * 토큰에는 사용자 ID(sub)와 발급·만료 시각만 넣는다. 역할·닉네임 여부는 요청마다 DB의 현재 값으로 판단한다.
 */
@Component
public class JwtProvider {

    // HS256 서명 키 최소 길이 (256비트)
    private static final int MIN_SECRET_KEY_BYTES = 32;

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final Clock clock;
    private final JwtProperties jwtProperties;

    public JwtProvider(JwtProperties jwtProperties, Clock clock) {

        SecretKey secretKey = toSecretKey(jwtProperties.secretKey());

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        // 만료는 getUserId에서 Clock으로 직접 판단해 EXPIRED_TOKEN과 INVALID_TOKEN을 구분한다
        decoder.setJwtValidator(jwt -> OAuth2TokenValidatorResult.success());

        this.jwtEncoder = NimbusJwtEncoder.withSecretKey(secretKey).algorithm(MacAlgorithm.HS256).build();
        this.jwtDecoder = decoder;
        this.clock = clock;
        this.jwtProperties = jwtProperties;
    }

    public String createAccessToken(Long userId) {

        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiresAt(now.plus(jwtProperties.accessTokenTtl()))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();

        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /**
     * 서명·형식이 올바르지 않으면 INVALID_TOKEN, 만료되었으면 EXPIRED_TOKEN
     */
    public Long getUserId(String token) {

        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(token);
        } catch (JwtException e) {
            throw new CustomException(INVALID_TOKEN);
        }

        Instant expiresAt = jwt.getExpiresAt();
        if (expiresAt == null || jwt.getSubject() == null) {
            throw new CustomException(INVALID_TOKEN);
        }
        if (!clock.instant().isBefore(expiresAt)) {
            throw new CustomException(EXPIRED_TOKEN);
        }

        try {
            return Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException e) {
            throw new CustomException(INVALID_TOKEN);
        }
    }

    public long getAccessTokenExpiresInSec() {
        return jwtProperties.accessTokenTtl().toSeconds();
    }

    // 키 값은 메시지에 넣지 않는다
    private static SecretKey toSecretKey(String secretKey) {

        if (secretKey == null || secretKey.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_KEY_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET_KEY 환경 변수가 없거나 " + MIN_SECRET_KEY_BYTES + "바이트보다 짧습니다. "
                            + "README의 '로컬 실행 > 백엔드'를 참고해 설정하세요. (예: openssl rand -base64 32)");
        }

        return new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
