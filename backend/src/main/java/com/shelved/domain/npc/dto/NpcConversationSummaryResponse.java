package com.shelved.domain.npc.dto;

import java.time.LocalDateTime;

/**
 * 세션 상태(GET state)의 conversations 항목. 대화 기록이 있는 NPC만 포함한다
 *
 * @param messageCount  질문·답변을 합한 메시지 수 (양의 정수)
 * @param lastMessageAt 마지막 메시지 시각
 */
public record NpcConversationSummaryResponse(
        Long suspectId,
        long messageCount,
        LocalDateTime lastMessageAt
) {
}
