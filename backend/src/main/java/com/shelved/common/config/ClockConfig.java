package com.shelved.common.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 게임 규칙 판정에 쓰는 현재 시각은 이 Clock으로만 구한다 (backend.md 12)
 * 테스트에서는 Clock.fixed(...)로 교체해 시간을 고정한다.
 */
@Configuration
public class ClockConfig {

    public static final ZoneId SERVER_ZONE_ID = ZoneId.of("Asia/Seoul");

    @Bean
    public Clock clock() {
        return Clock.system(SERVER_ZONE_ID);
    }
}
