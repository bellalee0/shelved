package com.shelved.domain.room.dto;

import com.shelved.common.enums.RoomStatus;
import java.time.LocalDateTime;

/**
 * 시간 관련 API 공통 응답 (docs/api/room.md RoomTimerResponse). 정산이 끝난 세션으로 만든다 (RoomTimeSettler.timerOf)
 *
 * @param awaitingGrading 남은 시간 0 + 미해결(PENDING·ERROR) 지목 있음 + 최종 종료 전
 */
public record RoomTimerResponse(
        RoomStatus status,
        int timeLimitSec,
        long elapsedActiveMs,
        long remainingMs,
        boolean awaitingGrading,
        LocalDateTime serverTime
) {
}
