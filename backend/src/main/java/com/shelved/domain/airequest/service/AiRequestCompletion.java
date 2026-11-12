package com.shelved.domain.airequest.service;

import tools.jackson.databind.JsonNode;

/**
 * 확정 트랜잭션의 결과
 */
public sealed interface AiRequestCompletion {

    record Succeeded(JsonNode result) implements AiRequestCompletion {
    }

    /**
     * 세션이 이미 최종 종료됨. 게임 데이터·횟수에 반영하지 않았다
     */
    record RoomEnded() implements AiRequestCompletion {
    }

    /**
     * 이전 실행의 늦은 응답(실행 번호가 최신이 아니거나 이미 PROCESSING이 아님). 반영하지 않았다
     */
    record Stale() implements AiRequestCompletion {
    }
}
