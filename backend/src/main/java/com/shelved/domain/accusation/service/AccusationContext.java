package com.shelved.domain.accusation.service;

/**
 * 지목 채점 요청 1회의 컨텍스트 (메모리에만 있고 저장하지 않는다). 채점 기준·답안이 담기므로 로그에 남기지 않는다
 *
 * @param roomId       세션
 * @param requestId    요청 식별자
 * @param accusationId 접수(또는 재채점 접수)한 지목 행. 실패 응답 detail에 쓴다
 * @param grading      채점 컨텍스트
 */
public record AccusationContext(
        Long roomId,
        String requestId,
        Long accusationId,
        GradingContext grading
) {

    @Override
    public String toString() {
        return "AccusationContext[roomId=" + roomId + ", requestId=" + requestId + ", accusationId=" + accusationId + "]";
    }
}
