package com.shelved.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 주기 작업(@Scheduled) 활성화. 테스트 프로필은 shelved.scheduling.enabled=false로 끄고 서비스 메서드를 직접 호출해 검증한다
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "shelved.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
