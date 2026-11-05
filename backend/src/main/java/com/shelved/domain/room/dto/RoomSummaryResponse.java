package com.shelved.domain.room.dto;

import com.shelved.common.enums.Difficulty;
import com.shelved.common.enums.GradingStatus;
import com.shelved.common.enums.RoomStatus;
import java.time.LocalDateTime;

/**
 * 내 세션 목록 항목 (GET /api/rooms). 값이 여러 엔티티에서 오므로 서비스에서 생성자로 만든다
 *
 * @param pendingGradingStatus PENDING·ERROR 지목이 있을 때만 그 값, 없으면 null
 * @param endedAt              PLAY_RESULTS가 있을 때만
 * @param durationSec          PLAY_RESULTS가 있을 때만
 */
public record RoomSummaryResponse(
        Long roomId,
        Long caseId,
        String displayTitle,
        Difficulty difficulty,
        String coverImageUrl,
        RoomStatus status,
        long remainingMs,
        GradingStatus pendingGradingStatus,
        LocalDateTime endedAt,
        Integer durationSec,
        LocalDateTime createdAt
) {
}
