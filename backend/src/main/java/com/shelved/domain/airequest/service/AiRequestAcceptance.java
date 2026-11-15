package com.shelved.domain.airequest.service;

import com.shelved.domain.airequest.dto.AiRequestStatusResponse;
import tools.jackson.databind.JsonNode;

/**
 * 접수 트랜잭션의 결과
 */
public sealed interface AiRequestAcceptance<C> {

    /**
     * 같은 requestId·입력이 이미 성공함 → 저장된 스냅샷 (다시 실행·차감하지 않음)
     */
    record Replayed<C>(JsonNode result) implements AiRequestAcceptance<C> {
    }

    /**
     * 같은 requestId·입력이 처리 중
     */
    record InProgress<C>(AiRequestStatusResponse status) implements AiRequestAcceptance<C> {
    }

    /**
     * 새로 접수했거나 실패한 요청을 다시 실행함 → AI를 호출한다
     */
    record Started<C>(AiRequestExecution<C> execution) implements AiRequestAcceptance<C> {
    }
}
