package com.shelved.common.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Claude API 설정값 (shelved.llm)
 * apiKey·model·filterModel은 환경 변수로 주입하며, 비어 있어도 애플리케이션은 기동된다.
 *
 * @param processingRecoveryMargin PROCESSING 요청 복구 기준 = 각 시간 제한 + 이 값
 * @param maxInputTokens           호출 1회의 입력 토큰 상한
 * @param maxOutputTokens          호출 1회의 출력 토큰 상한
 */
@ConfigurationProperties(prefix = "shelved.llm")
public record LlmProperties(
        String apiKey,
        String model,
        String filterModel,
        Duration gameTimeout,
        Duration casebuilderChatTimeout,
        Duration processingRecoveryMargin,
        int maxInputTokens,
        int maxOutputTokens
) {

    // API 키가 로그에 남지 않도록 record 기본 toString을 대체한다
    @Override
    public String toString() {
        return "LlmProperties[apiKey=" + (apiKey == null || apiKey.isBlank() ? "(empty)" : "****")
                + ", model=" + model
                + ", filterModel=" + filterModel
                + ", gameTimeout=" + gameTimeout
                + ", casebuilderChatTimeout=" + casebuilderChatTimeout
                + ", processingRecoveryMargin=" + processingRecoveryMargin
                + ", maxInputTokens=" + maxInputTokens
                + ", maxOutputTokens=" + maxOutputTokens + "]";
    }
}
