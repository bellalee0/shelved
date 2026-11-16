package com.shelved.domain.hint.dto;

import com.shelved.domain.airequest.dto.AiRequestStatusResponse;
import com.shelved.domain.room.dto.RoomStateResponse.RoomUsageCount;
import java.util.List;

/**
 * 힌트 기록 (docs/api/hint-accusation.md GET hints)
 *
 * @param hints           오래된 순
 * @param pendingRequests PROCESSING이거나 재시도 대기 중인 FAILED 새 힌트·보충 질문 요청. 없으면 []
 */
public record HintListResponse(
        RoomUsageCount hintCount,
        List<HintResponse> hints,
        List<AiRequestStatusResponse> pendingRequests
) {
}
