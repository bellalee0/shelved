package com.shelved.domain.llmusage.service;

import com.shelved.common.enums.LlmCallStatus;
import com.shelved.common.enums.LlmRequestType;

/**
 * LLM 호출 1회의 사용량 기록 입력. 토큰을 모르면 null (0으로 채우지 않는다)
 *
 * @param roomId    게임 호출의 세션 (관리자 제작 호출은 null)
 * @param caseId    사건
 * @param errorCode 실패 구분 (성공이면 null)
 */
public record LlmUsageRecord(
        LlmRequestType requestType,
        String model,
        Long roomId,
        Long caseId,
        Integer inputTokens,
        Integer outputTokens,
        LlmCallStatus callStatus,
        String errorCode
) {
}
