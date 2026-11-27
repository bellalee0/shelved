package com.shelved.domain.accusation.dto;

import com.shelved.domain.room.dto.RoomStateResponse.RoomUsageCount;
import java.util.List;

/**
 * 지목 기록 (GET accusations)
 *
 * @param accusations 오래된 순
 */
public record AccusationListResponse(
        RoomUsageCount accusationCount,
        List<AccusationResponse> accusations
) {
}
