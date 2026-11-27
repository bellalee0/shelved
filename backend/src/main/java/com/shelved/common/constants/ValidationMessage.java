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

    // --- AI 요청 ---
    // requestId 형식 (UUID, 대소문자 무관)
    public static final String UUID_REGEX = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";
    public static final String REQUEST_ID_NOT_BLANK = "요청 식별자는 필수입니다.";
    public static final String REQUEST_ID_PATTERN = "요청 식별자 형식이 올바르지 않습니다.";

    // --- 질문 (NPC 질문·새 힌트·보충 질문 공통) ---
    public static final String QUESTION_NOT_BLANK = "질문 내용은 필수입니다.";
    public static final String QUESTION_SIZE = "질문은 500자 이하로 입력해 주세요.";

    // --- 지목 ---
    public static final String SUSPECT_ID_NOT_NULL = "지목할 인물을 선택해 주세요.";
    public static final String TRICK_EXPLANATION_NOT_BLANK = "트릭 설명은 필수입니다.";
    // 글자 수는 앞뒤 공백을 뺀 길이로 센다 (화면의 text.trim().length와 같은 기준)
    public static final String TRICK_EXPLANATION_SIZE = "트릭 설명은 10~3000자로 입력해 주세요.";

    // --- 노트 ---
    public static final String NOTE_CONTENT_NOT_NULL = "노트 내용은 필수입니다.";
    public static final String NOTE_REVISION_NOT_NULL = "노트 버전은 필수입니다.";
    public static final String NOTE_REVISION_POSITIVE_OR_ZERO = "노트 버전은 0 이상이어야 합니다.";
}
