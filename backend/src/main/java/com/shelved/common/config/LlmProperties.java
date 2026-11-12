package com.shelved.common.config;

import com.shelved.common.enums.LlmProvider;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Claude API 설정값 (shelved.llm)
 * apiKey·model·filterModel은 환경 변수로 주입한다. provider=fake(기본값)이면 비어 있어도 기동되고,
 * provider=claude이면 비어 있을 때 기동에 실패한다 (LlmClientConfig).
 *
 * @param provider                   LLM 호출 제공자. 없으면 FAKE
 * @param processingRecoveryMargin   PROCESSING 요청 복구 기준 = 각 시간 제한 + 이 값
 * @param processingRecoveryInterval 오래된 PROCESSING 요청 복구 점검 주기
 * @param maxInputTokens             호출 1회의 입력 토큰 상한
 * @param maxOutputTokens            호출 1회의 출력 토큰 상한
 */
@ConfigurationProperties(prefix = "shelved.llm")
public record LlmProperties(
        LlmProvider provider,
        String apiKey,
        String model,
        String filterModel,
        Duration gameTimeout,
        Duration casebuilderChatTimeout,
        Duration processingRecoveryMargin,
        Duration processingRecoveryInterval,
        int maxInputTokens,
        int maxOutputTokens
) {

    public LlmProperties {
        if (provider == null) {
            provider = LlmProvider.FAKE;
        }
    }

    /**
     * 게임플레이 PROCESSING 요청을 실패로 정리하는 기준 시간 (game-timeout + processing-recovery-margin)
     */
    public Duration gameProcessingRecoveryAfter() {
        return gameTimeout.plus(processingRecoveryMargin);
    }

    // API 키가 로그에 남지 않도록 record 기본 toString을 대체한다
    @Override
    public String toString() {
        return "LlmProperties[provider=" + provider
                + ", apiKey=" + (apiKey == null || apiKey.isBlank() ? "(empty)" : "****")
                + ", model=" + model
                + ", filterModel=" + filterModel
                + ", gameTimeout=" + gameTimeout
                + ", casebuilderChatTimeout=" + casebuilderChatTimeout
                + ", processingRecoveryMargin=" + processingRecoveryMargin
                + ", processingRecoveryInterval=" + processingRecoveryInterval
                + ", maxInputTokens=" + maxInputTokens
                + ", maxOutputTokens=" + maxOutputTokens + "]";
    }
}
