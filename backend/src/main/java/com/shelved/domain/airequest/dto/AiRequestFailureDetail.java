package com.shelved.domain.airequest.dto;

/**
 * AI 요청 실패(502 유형별 코드)와 409 AI_REQUEST_IN_PROGRESS의 detail
 *
 * @param requestId 실패한 요청, 또는 처리 중인 요청
 */
public record AiRequestFailureDetail(
        String requestId
) {
}
