package com.shelved.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 개발용 시드 설정값 (shelved.dev-seed). local 프로필의 시드 로더만 사용한다.
 *
 * @param path 시드 JSON 폴더 (Spring 리소스 경로, 끝에 / 포함). 이 폴더의 *.json을 모두 읽는다
 */
@ConfigurationProperties(prefix = "shelved.dev-seed")
public record DevSeedProperties(
        @DefaultValue("classpath:dev-data/") String path
) {
}
