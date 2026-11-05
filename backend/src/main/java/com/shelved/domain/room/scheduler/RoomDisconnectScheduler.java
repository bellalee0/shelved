package com.shelved.domain.room.scheduler;

import com.shelved.domain.room.service.RoomLifecycleService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 이탈 감지: 하트비트가 heartbeat-timeout 이상 끊긴 IN_PROGRESS 세션을 마지막 접속 확인 시각 기준으로 일시정지한다.
 * 로직은 RoomLifecycleService에 있고 여기서는 호출만 한다 (backend.md 패키지 구조)
 */
@Component
@RequiredArgsConstructor
public class RoomDisconnectScheduler {

    private final RoomLifecycleService roomLifecycleService;

    @Scheduled(fixedDelayString = "${shelved.game.disconnect-check-interval}")
    public void pauseDisconnectedRooms() {
        roomLifecycleService.pauseDisconnectedRooms();
    }
}
