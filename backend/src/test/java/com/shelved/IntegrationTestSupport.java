package com.shelved;

import com.shelved.common.config.ClockConfig;
import com.shelved.support.MutableTestClock;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

/**
 * 통합 테스트 기반 클래스. test 프로필 + Testcontainers PostgreSQL (로컬 Docker 필요)
 * 상속한 테스트끼리는 같은 스프링 컨텍스트와 컨테이너를 재사용한다.
 * 클래스명은 {대상}IntegrationTest로 한다 (backend.md 테스트 코드 작성 규칙).
 * Security 필터를 포함한 요청 테스트를 위해 MockMvc를 함께 구성한다 (컨텍스트를 하나로 공유하기 위해 여기에 둔다).
 * Clock은 조정 가능한 MutableTestClock으로 바꾼다. 고정하지 않으면 시스템 시각이며, 매 테스트 후 되돌린다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, IntegrationTestSupport.TestClockConfiguration.class})
public abstract class IntegrationTestSupport {

    @Autowired
    protected MutableTestClock testClock;

    @AfterEach
    void resetTestClock() {
        testClock.reset();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestClockConfiguration {

        @Bean
        @Primary
        MutableTestClock testClock() {
            return new MutableTestClock(ClockConfig.SERVER_ZONE_ID);
        }
    }
}
