package com.shelved.infra.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.shelved.common.config.GoogleProperties;
import com.shelved.common.exception.CustomException;
import com.shelved.common.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

class NimbusGoogleIdTokenVerifierTest {

    private static final String CLIENT_ID = "000000000000-example.apps.googleusercontent.com";
    private static final String ISSUER = "https://accounts.google.com";
    private static final Instant NOW = Instant.parse("2026-10-30T03:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneId.of("Asia/Seoul"));

    private static RSAKey signingKey;
    private static RSAKey otherKey;

    private final NimbusGoogleIdTokenVerifier verifier = createVerifier(CLIENT_ID);

    @BeforeAll
    static void generateKeys() throws JOSEException {
        signingKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
        otherKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
    }

    @Test
    void verify_정상토큰_sub반환() throws Exception {

        // given
        String token = sign(signingKey, claims(ISSUER, CLIENT_ID, NOW.plusSeconds(600)).build());

        // when
        String sub = verifier.verify(token);

        // then
        assertThat(sub).isEqualTo("google-sub-1");
    }

    @Test
    void verify_발급자가_스킴없는_도메인_sub반환() throws Exception {

        // given
        String token = sign(signingKey, claims("accounts.google.com", CLIENT_ID, NOW.plusSeconds(600)).build());

        // when
        String sub = verifier.verify(token);

        // then
        assertThat(sub).isEqualTo("google-sub-1");
    }

    @Test
    void verify_다른키로_서명_예외() throws Exception {

        // given
        String token = sign(otherKey, claims(ISSUER, CLIENT_ID, NOW.plusSeconds(600)).build());

        // when & then
        assertInvalid(token);
    }

    @Test
    void verify_발급자다름_예외() throws Exception {

        // given
        String token = sign(signingKey, claims("https://evil.example.com", CLIENT_ID, NOW.plusSeconds(600)).build());

        // when & then
        assertInvalid(token);
    }

    @Test
    void verify_대상이_다른_clientId_예외() throws Exception {

        // given
        String token = sign(signingKey,
                claims(ISSUER, "111111111111-other.apps.googleusercontent.com", NOW.plusSeconds(600)).build());

        // when & then
        assertInvalid(token);
    }

    @Test
    void verify_만료됨_예외() throws Exception {

        // given
        String token = sign(signingKey, claims(ISSUER, CLIENT_ID, NOW.minusSeconds(3600)).build());

        // when & then
        assertInvalid(token);
    }

    @Test
    void verify_exp없음_예외() throws Exception {

        // given
        JWTClaimsSet noExp = new JWTClaimsSet.Builder()
                .issuer(ISSUER).audience(CLIENT_ID).subject("google-sub-1").build();
        String token = sign(signingKey, noExp);

        // when & then
        assertInvalid(token);
    }

    @Test
    void verify_sub없음_예외() throws Exception {

        // given
        JWTClaimsSet noSub = new JWTClaimsSet.Builder()
                .issuer(ISSUER).audience(CLIENT_ID)
                .issueTime(Date.from(NOW.minusSeconds(60)))
                .expirationTime(Date.from(NOW.plusSeconds(600))).build();
        String token = sign(signingKey, noSub);

        // when & then
        assertInvalid(token);
    }

    @Test
    void verify_sub가_공백_예외() throws Exception {

        // given
        JWTClaimsSet blankSub = claims(ISSUER, CLIENT_ID, NOW.plusSeconds(600)).subject("   ").build();
        String token = sign(signingKey, blankSub);

        // when & then
        assertInvalid(token);
    }

    @Test
    void verify_clientId비어있음_예외() throws Exception {

        // given
        NimbusGoogleIdTokenVerifier noClientId = createVerifier("");
        String token = sign(signingKey, claims(ISSUER, CLIENT_ID, NOW.plusSeconds(600)).build());

        // when & then
        assertThatThrownBy(() -> noClientId.verify(token))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.GOOGLE_TOKEN_INVALID));
    }

    @Test
    void verify_형식이깨진문자열_예외() {

        // when & then
        assertInvalid("not-a-jwt");
    }

    private void assertInvalid(String token) {
        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.GOOGLE_TOKEN_INVALID));
    }

    private static NimbusGoogleIdTokenVerifier createVerifier(String clientId) {
        try {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(signingPublicKey()).build();
            return new NimbusGoogleIdTokenVerifier(new GoogleProperties(clientId), CLOCK, decoder);
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private static java.security.interfaces.RSAPublicKey signingPublicKey() throws JOSEException {
        return signingKey.toRSAPublicKey();
    }

    private static JWTClaimsSet.Builder claims(String issuer, String audience, Instant expiresAt) {
        return new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience(audience)
                .subject("google-sub-1")
                .issueTime(Date.from(NOW.minusSeconds(60)))
                .expirationTime(Date.from(expiresAt));
    }

    private static String sign(RSAKey key, JWTClaimsSet claims) throws JOSEException {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }
}
