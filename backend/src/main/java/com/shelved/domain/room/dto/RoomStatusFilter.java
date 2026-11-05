package com.shelved.domain.room.dto;

import com.shelved.common.enums.RoomStatus;
import java.util.EnumSet;
import java.util.Set;

/**
 * 세션 목록의 상태 필터 (GET /api/rooms?status=). 미종료·종료 상태 묶음으로도 쓴다
 */
public enum RoomStatusFilter {

    ACTIVE(EnumSet.of(RoomStatus.READY, RoomStatus.IN_PROGRESS, RoomStatus.PAUSED)),
    ENDED(EnumSet.of(RoomStatus.CLEARED, RoomStatus.FAILED, RoomStatus.EXPIRED)),
    ;

    private final Set<RoomStatus> statuses;

    RoomStatusFilter(Set<RoomStatus> statuses) {
        this.statuses = statuses;
    }

    public Set<RoomStatus> statuses() {
        return statuses;
    }

    public static boolean isEnded(RoomStatus status) {
        return ENDED.statuses.contains(status);
    }

    /**
     * 필터가 없으면(null) 전체 상태
     */
    public static Set<RoomStatus> statusesOf(RoomStatusFilter filter) {
        return filter == null ? EnumSet.allOf(RoomStatus.class) : filter.statuses;
    }
}
