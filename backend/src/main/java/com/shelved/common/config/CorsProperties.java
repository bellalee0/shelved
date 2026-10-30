package com.shelved.common.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CORS 설정값 (shelved.cors)
 *
 * @param allowedOrigins 브라우저에서 API를 호출할 수 있는 출처 (예: http://localhost:5173)
 */
@ConfigurationProperties(prefix = "shelved.cors")
public record CorsProperties(
        List<String> allowedOrigins
) {
}
