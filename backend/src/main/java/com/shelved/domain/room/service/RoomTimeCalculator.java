package com.shelved.domain.room.service;

import com.shelved.common.entity.Room;
import com.shelved.common.enums.RoomStatus;
import com.shelved.domain.room.dto.RoomStatusFilter;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 세션 시간 계산 (backend.md 12). 상태를 바꾸지 않는 순수 계산만 하며, 현재 시각은 호출하는 쪽이 주입받은 Clock으로 구해 넘긴다.
 * 상태 전이·시간 정산(elapsed_active_ms 누적)·만료 판정은 이 클래스가 하지 않는다.
 */
public final class RoomTimeCalculator {

    private RoomTimeCalculator() {
    }

    /**
     * 계산 결과
     *
     * @param elapsedActiveMs 일시정지를 제외한 누적 진행 시간 (현재 진행 구간 포함)
     * @param remainingMs     남은 시간 (0 이상). 종료 상태는 0
     */
    public record RoomTime(
            long elapsedActiveMs,
            long remainingMs
    ) {
    }

    public static RoomTime calculate(Room room, LocalDateTime now) {
        return calculate(room.getStatus(), room.getTimeLimitSec(), room.getElapsedActiveMs(), room.getActiveSince(), now);
    }

    /**
     * elapsed = elapsedActiveMs + (IN_PROGRESS이고 activeSince가 있으면 now - activeSince, 아니면 0)
     * remaining = max(0, timeLimitSec * 1000 - elapsed), 종료 상태는 0
     */
    public static RoomTime calculate(
            RoomStatus status,
            int timeLimitSec,
            long elapsedActiveMs,
            LocalDateTime activeSince,
            LocalDateTime now
    ) {
        long elapsed = elapsedActiveMs + currentSegmentMs(status, activeSince, now);
        long remaining = RoomStatusFilter.isEnded(status)
                ? 0L
                : Math.max(0L, timeLimitSec * 1000L - elapsed);

        return new RoomTime(elapsed, remaining);
    }

    /**
     * 진행 중인 구간의 길이. 시각이 거꾸로 가도(now < activeSince) 음수를 더하지 않는다
     */
    public static long currentSegmentMs(RoomStatus status, LocalDateTime activeSince, LocalDateTime now) {
        if (status != RoomStatus.IN_PROGRESS || activeSince == null) {
            return 0L;
        }
        return Math.max(0L, Duration.between(activeSince, now).toMillis());
    }

    /**
     * 밀리초 → 초 (내림). PLAY_RESULTS.duration_sec 계산용
     */
    public static int toDurationSec(long millis) {
        return Math.toIntExact(Math.floorDiv(Math.max(0L, millis), 1000L));
    }
}
