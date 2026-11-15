package com.shelved.infra.llm;

/**
 * LLM 호출 실패 구분. 이름을 사용량 로그의 error_code로 저장한다
 */
public enum LlmErrorType {
    // 설정한 시간 제한 안에 응답이 오지 않음
    LLM_TIMEOUT,
    // 연결 실패, 4xx·5xx 응답 등 서비스 오류
    LLM_SERVICE_ERROR,
    // 응답 본문을 해석할 수 없거나 텍스트가 없음
    LLM_INVALID_RESPONSE,
    // 입력이 shelved.llm.max-input-tokens를 넘음 (잘라 보내지 않고 호출 전에 실패)
    LLM_INPUT_TOO_LARGE
}
