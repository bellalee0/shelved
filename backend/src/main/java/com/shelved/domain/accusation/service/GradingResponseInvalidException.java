package com.shelved.domain.accusation.service;

import com.shelved.domain.airequest.service.AiResponseInvalidException;

/**
 * 채점 응답 형식 오류. AiRequestExecutor가 AiResponseInvalidException으로 받아 채점 실패(ERROR·502 GRADING_FAILED)로 처리한다
 * (오답·기회 소모 아님). 메시지는 구분 코드뿐이며 응답 본문·답안·피드백은 넣지 않는다
 */
public class GradingResponseInvalidException extends AiResponseInvalidException {

    private final Reason reason;

    public GradingResponseInvalidException(Reason reason) {
        super("GRADING_" + reason.name());
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }

    /**
     * 형식 오류 구분 코드 (로그용)
     */
    public enum Reason {
        // JSON 객체가 아님
        NOT_JSON,
        // items·feedback 또는 항목의 필드가 없거나 타입이 다름
        MISSING_FIELD,
        // result가 MET/MISSING/INCORRECT가 아님
        INVALID_RESULT,
        // 기대 항목이 응답에 없음
        MISSING_ITEM,
        // 채점 기준에 없는 항목
        UNKNOWN_ITEM,
        // 같은 항목이 두 번 이상
        DUPLICATE_ITEM,
        // reason이 비었음
        BLANK_REASON,
        // MET·INCORRECT인데 근거 구절이 비었음
        BLANK_EVIDENCE,
        // 피드백이 비었거나 공백뿐
        BLANK_FEEDBACK,
        // 피드백이 상한 초과
        FEEDBACK_TOO_LONG
    }
}
