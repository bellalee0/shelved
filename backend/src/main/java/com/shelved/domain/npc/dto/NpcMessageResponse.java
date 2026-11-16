package com.shelved.domain.npc.dto;

import com.shelved.common.entity.NpcMessage;
import com.shelved.common.enums.MessageSenderType;
import java.time.LocalDateTime;

/**
 * NPC 대화 메시지 1개 (docs/api/investigation.md NpcMessageResponse)
 *
 * @param sequenceNo 이 NPC 대화 안의 순번 (정렬 기준)
 */
public record NpcMessageResponse(
        Long messageId,
        MessageSenderType senderType,
        String content,
        int sequenceNo,
        LocalDateTime createdAt
) {

    public static NpcMessageResponse from(NpcMessage message) {
        return new NpcMessageResponse(
                message.getId(),
                message.getSenderType(),
                message.getContent(),
                message.getSequenceNo(),
                message.getCreatedAt()
        );
    }
}
