package com.shelved.common.enums;

/**
 * AI 사용량 로그 유형. QUESTION_FILTER는 직접적인 정답 요구 차단 판정 호출
 */
public enum LlmRequestType {
    NPC_CHAT,
    HINT,
    HINT_FOLLOWUP,
    GRADING,
    CASE_GENERATION,
    QUESTION_FILTER
}
