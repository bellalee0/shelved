package com.shelved.domain.npc.dto;

import com.shelved.domain.airequest.dto.AiRequestStatusResponse;
import java.util.List;

/**
 * 한 NPC와의 대화 원문 (docs/api/investigation.md GET messages)
 *
 * @param messages        sequenceNo 순
 * @param pendingRequests 이 NPC에게 보낸 질문 중 PROCESSING이거나 재시도 대기 중인 FAILED 요청. 없으면 []
 */
public record NpcMessageListResponse(
        Long suspectId,
        String suspectName,
        List<NpcMessageResponse> messages,
        List<AiRequestStatusResponse> pendingRequests
) {
}
