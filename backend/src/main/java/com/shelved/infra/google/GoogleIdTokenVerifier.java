package com.shelved.infra.google;

/**
 * Google ID 토큰 검증 클라이언트. 테스트에서는 대체 구현(Mock)으로 바꾼다.
 */
public interface GoogleIdTokenVerifier {

    /**
     * 발급자·대상 client ID·만료를 검증하고 Google 사용자 식별자(sub)를 반환한다.
     * 검증에 실패하면 CustomException(GOOGLE_TOKEN_INVALID)
     */
    String verify(String idToken);
}
