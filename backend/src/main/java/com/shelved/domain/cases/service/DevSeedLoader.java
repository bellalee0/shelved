package com.shelved.domain.cases.service;

import com.shelved.common.config.DevSeedProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * local 프로필 기동 완료 후 개발용 시드를 1회 반영한다 (README 개발용 시드).
 * 같은 표시명의 공개 사건이 이미 있으면 건너뛰므로 기동할 때마다 다시 넣지 않는다.
 */
@Profile("local")
@Component
@RequiredArgsConstructor
public class DevSeedLoader {

    private final DevSeedService devSeedService;
    private final DevSeedProperties devSeedProperties;

    @EventListener(ApplicationReadyEvent.class)
    public void loadDevSeeds() {
        devSeedService.loadAll(devSeedProperties.path());
    }
}
