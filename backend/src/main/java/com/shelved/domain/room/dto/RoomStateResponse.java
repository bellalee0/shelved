package com.shelved.domain.room.dto;

import com.shelved.common.enums.Difficulty;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 세션 상태 (재접속 복원). pendingAccusation·aiRequests·conversations는 형식이 정해지는 W3~W4에서 채운다
 *
 * @param pendingAccusation 채점 중·채점 실패 지목 (W4: AccusationResponse). 2주차에는 null
 * @param aiRequests        처리 중·재시도 대기 AI 요청 (W3: AiRequestStatusResponse). 2주차에는 빈 배열
 * @param conversations     대화한 NPC 목록 (W3). 2주차에는 빈 배열
 */
public record RoomStateResponse(
        Long roomId,
        Long caseId,
        String displayTitle,
        Difficulty difficulty,
        boolean isTest,
        LocalDateTime startedAt,
        RoomTimerResponse timer,
        long heartbeatIntervalMs,
        RoomUsageCount hintCount,
        RoomUsageCount accusationCount,
        Object pendingAccusation,
        List<Object> aiRequests,
        List<Object> conversations
) {

    /**
     * 횟수 현황 (docs/api/room.md UsageCount). remaining = limit - used - reserved (0 미만이면 0)
     */
    public record RoomUsageCount(
            int limit,
            int used,
            int reserved,
            int remaining
    ) {

        public RoomUsageCount(int limit, long used, long reserved) {
            this(limit, Math.toIntExact(used), Math.toIntExact(reserved),
                    Math.max(0, limit - Math.toIntExact(used) - Math.toIntExact(reserved)));
        }
    }
}
