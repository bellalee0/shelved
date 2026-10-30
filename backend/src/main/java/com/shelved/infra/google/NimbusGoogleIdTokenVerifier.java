package com.shelved.infra.google;

import static com.shelved.common.exception.ErrorCode.GOOGLE_TOKEN_INVALID;

import com.shelved.common.config.GoogleProperties;
import com.shelved.common.exception.CustomException;
import java.time.Clock;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Google 공개키(JWK Set)로 ID 토큰 서명을 검증한다. 공개키는 첫 요청 때 받아 캐시한다.
 * GOOGLE_CLIENT_ID가 비어 있어도 서버는 기동되고, 로그인 요청만 GOOGLE_TOKEN_INVALID로 실패한다.
 */
@Slf4j
@Component
public class NimbusGoogleIdTokenVerifier implements GoogleIdTokenVerifier {

    private static final String JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs";
    private static final Set<String> ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");

    // GOOGLE_CLIENT_ID가 없으면 null
    private final JwtDecoder jwtDecoder;

    public NimbusGoogleIdTokenVerifier(GoogleProperties googleProperties, Clock clock) {

        if (!StringUtils.hasText(googleProperties.clientId())) {
            log.warn("GOOGLE_CLIENT_ID가 설정되지 않아 Google 로그인 요청은 모두 실패합니다.");
            this.jwtDecoder = null;
            return;
        }

        JwtTimestampValidator timestampValidator = new JwtTimestampValidator();
        timestampValidator.setClock(clock);
        timestampValidator.setAllowEmptyExpiryClaim(false);

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(JWK_SET_URI).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                timestampValidator,
                new JwtClaimValidator<Object>(JwtClaimNames.ISS, iss -> iss != null && ISSUERS.contains(iss.toString())),
                new JwtAudienceValidator(googleProperties.clientId())
        ));

        this.jwtDecoder = decoder;
    }

    @Override
    public String verify(String idToken) {

        if (jwtDecoder == null) {
            throw new CustomException(GOOGLE_TOKEN_INVALID);
        }

        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(idToken);
        } catch (JwtException e) {
            // 예외 메시지에 토큰 내용이 섞일 수 있어 종류만 남긴다
            log.info("Google ID 토큰 검증 실패: {}", e.getClass().getSimpleName());
            throw new CustomException(GOOGLE_TOKEN_INVALID);
        }

        if (!StringUtils.hasText(jwt.getSubject())) {
            throw new CustomException(GOOGLE_TOKEN_INVALID);
        }

        return jwt.getSubject();
    }
}
