package com.shelved.common.enums;

import com.shelved.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 게임플레이 AI 요청 유형. 유형별 실패 코드(502)는 이 한 곳에서 정한다
 */
@Getter
@RequiredArgsConstructor
public enum AiRequestType {
    NPC_ASK(ErrorCode.NPC_ANSWER_FAILED),
    HINT(ErrorCode.HINT_GENERATION_FAILED),
    HINT_FOLLOWUP(ErrorCode.HINT_FOLLOWUP_FAILED),
    ACCUSATION(ErrorCode.GRADING_FAILED);

    // AI 호출 실패·시간 초과·형식 오류·작업 복구 시 AI_REQUESTS.error_code와 응답에 쓰는 코드
    private final ErrorCode failureCode;
}
