package com.shelved.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 정적 에셋 설정값 (shelved.assets)
 *
 * @param basePath 프론트엔드 정적 에셋 폴더 (manifest.json·맵 파일 검증용)
 */
@ConfigurationProperties(prefix = "shelved.assets")
public record AssetsProperties(
        String basePath
) {
}
