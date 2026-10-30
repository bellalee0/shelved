package com.shelved.common.constants;

/**
 * 검증 메시지. 이름 규칙은 {필드}_{조건}
 * 도메인별 주석으로 묶어 각 도메인 담당이 추가한다.
 */
public final class ValidationMessage {

    private ValidationMessage() {
    }

    // --- 공통 ---
    // 요청 본문 필드의 타입이 맞지 않을 때 (예: 정수 필드에 문자열·소수)
    public static final String INPUT_TYPE_MISMATCH = "입력값의 형식이 올바르지 않습니다.";
}
