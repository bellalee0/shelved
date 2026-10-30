package com.shelved;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * 통합 테스트 기반 클래스. test 프로필 + Testcontainers PostgreSQL (로컬 Docker 필요)
 * 상속한 테스트끼리는 같은 스프링 컨텍스트와 컨테이너를 재사용한다.
 * 클래스명은 {대상}IntegrationTest로 한다 (backend.md 테스트 코드 작성 규칙).
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTestSupport {
}
