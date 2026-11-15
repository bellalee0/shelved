package com.shelved.infra.llm;

/**
 * 사용량 로그에 남길 호출 대상. 게임 호출은 roomId·caseId, 관리자 제작 호출은 caseId만
 */
public record LlmCallContext(
        Long roomId,
        Long caseId
) {

    public static LlmCallContext game(Long roomId, Long caseId) {
        return new LlmCallContext(roomId, caseId);
    }

    public static LlmCallContext casebuilder(Long caseId) {
        return new LlmCallContext(null, caseId);
    }
}
