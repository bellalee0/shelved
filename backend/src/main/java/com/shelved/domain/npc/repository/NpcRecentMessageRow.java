package com.shelved.domain.npc.repository;

import com.shelved.common.enums.MessageSenderType;

/**
 * 세션의 NPC별 최근 대화 원문 1개 (NpcMessageRepository.findRecentMessageRowList). 내용은 로그에 남기지 않는다
 */
public record NpcRecentMessageRow(
        Long suspectId,
        String suspectName,
        MessageSenderType senderType,
        String content,
        int sequenceNo
) {

    // 대화 원문이 로그에 섞이지 않도록 record 기본 toString을 대체한다
    @Override
    public String toString() {
        return "NpcRecentMessageRow[suspectId=" + suspectId + ", senderType=" + senderType + ", sequenceNo=" + sequenceNo + "]";
    }
}
