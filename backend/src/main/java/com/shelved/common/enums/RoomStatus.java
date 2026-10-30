package com.shelved.common.enums;

/**
 * 세션 상태. CLEARED·FAILED·EXPIRED는 종료 상태
 */
public enum RoomStatus {
    READY,
    IN_PROGRESS,
    PAUSED,
    CLEARED,
    FAILED,
    EXPIRED
}
