package com.shelved.domain.npc.dto;

/**
 * NPC 질문 성공 응답. AI_REQUESTS.result_json 스냅샷으로 저장되어 같은 requestId 재전송·상태 조회에 그대로 쓰인다
 *
 * @param question 저장된 플레이어 질문
 * @param answer   저장된 NPC 답변
 */
public record NpcAskResponse(
        String requestId,
        Long suspectId,
        NpcMessageResponse question,
        NpcMessageResponse answer
) {
}
