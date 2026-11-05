package com.shelved.domain.room.dto;

/**
 * 수사 시작·이어하기 응답. connectionId는 이후 조작 API의 X-Connection-Id 헤더 값이다 (로그에 남기지 않는다)
 */
public record RoomConnectionResponse(
        String connectionId,
        long heartbeatIntervalMs,
        RoomTimerResponse timer
) {
}
