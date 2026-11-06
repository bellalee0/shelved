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

    // --- 인증 ---
    public static final String ID_TOKEN_NOT_BLANK = "Google ID 토큰은 필수입니다.";
    public static final String REFRESH_TOKEN_NOT_BLANK = "리프레시 토큰은 필수입니다.";

    // --- 사용자 ---
    public static final String NICKNAME_NOT_BLANK = "닉네임은 필수입니다.";
    public static final String NICKNAME_SIZE = "닉네임은 2~12자로 입력해 주세요.";
    public static final String NICKNAME_PATTERN = "닉네임은 한글, 영문, 숫자만 사용할 수 있습니다.";

    // --- 세션 ---
    public static final String CASE_ID_NOT_NULL = "사건을 선택해 주세요.";

    // --- 노트 ---
    public static final String NOTE_CONTENT_NOT_NULL = "노트 내용은 필수입니다.";
    public static final String NOTE_REVISION_NOT_NULL = "노트 버전은 필수입니다.";
    public static final String NOTE_REVISION_POSITIVE_OR_ZERO = "노트 버전은 0 이상이어야 합니다.";
}
