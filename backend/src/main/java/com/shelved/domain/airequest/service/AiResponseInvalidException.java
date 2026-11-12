package com.shelved.domain.airequest.service;

/**
 * AI 응답이 도메인이 기대하는 형식이 아님. AI 호출 실패로 처리한다 (오답·기회 소모로 처리하지 않음).
 * 메시지에는 응답 본문을 넣지 않는다
 */
public class AiResponseInvalidException extends RuntimeException {

    public AiResponseInvalidException(String reason) {
        super(reason);
    }
}
