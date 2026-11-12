package com.shelved.domain.airequest.service;

/**
 * 결과 확정 성공
 *
 * @param resultRefId 생성된 결과 행 ID (npc_messages·hints·hint_followups·accusations). 없으면 null
 * @param response    플레이어용 성공 응답 DTO. result_json 스냅샷으로 저장된다 (내부 프롬프트·정답 정보 금지)
 */
public record AiRequestSuccess(
        Long resultRefId,
        Object response
) {
}
