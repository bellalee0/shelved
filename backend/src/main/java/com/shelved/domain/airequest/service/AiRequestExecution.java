package com.shelved.domain.airequest.service;

/**
 * 접수한 실행 1회. 확정·실패 처리는 executionNo가 최신이고 PROCESSING일 때만 반영한다
 *
 * @param aiRequestId AI_REQUESTS.id
 * @param context     도메인이 접수 시 읽어 둔 컨텍스트 (로그에 남기지 않는다)
 */
public record AiRequestExecution<C>(
        Long roomId,
        Long caseId,
        Long aiRequestId,
        String requestId,
        int executionNo,
        C context
) {

    // 컨텍스트(프롬프트 재료)가 로그에 섞이지 않도록 record 기본 toString을 대체한다
    @Override
    public String toString() {
        return "AiRequestExecution[roomId=" + roomId + ", requestId=" + requestId + ", executionNo=" + executionNo + "]";
    }
}
