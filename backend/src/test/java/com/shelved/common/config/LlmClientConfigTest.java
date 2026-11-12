package com.shelved.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.shelved.infra.llm.ClaudeLlmClient;
import com.shelved.infra.llm.FakeLlmClient;
import com.shelved.infra.llm.LlmClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * provider 설정에 따른 LLM 클라이언트 선택과 기동 검증. 실제 API는 호출하지 않는다
 */
class LlmClientConfigTest {

    private static final String FAKE_KEY = "sk-ant-example-not-a-real-key";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class, LlmClientConfig.class)
            .withPropertyValues(
                    "shelved.llm.game-timeout=60s",
                    "shelved.llm.casebuilder-chat-timeout=300s",
                    "shelved.llm.processing-recovery-margin=60s",
                    "shelved.llm.processing-recovery-interval=30s",
                    "shelved.llm.max-input-tokens=150000",
                    "shelved.llm.max-output-tokens=8192");

    @Test
    void provider미지정_Fake클라이언트_키없이기동() {

        // when & then
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(LlmClient.class);
            assertThat(context.getBean(LlmClient.class)).isInstanceOf(FakeLlmClient.class);
        });
    }

    @Test
    void providerClaude_키와모델있음_Claude클라이언트() {

        // when & then
        contextRunner
                .withPropertyValues(
                        "shelved.llm.provider=claude",
                        "shelved.llm.api-key=" + FAKE_KEY,
                        "shelved.llm.model=example-main-model",
                        "shelved.llm.filter-model=example-filter-model")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(LlmClient.class)).isInstanceOf(ClaudeLlmClient.class);
                });
    }

    @Test
    void providerClaude_키비어있음_기동실패_값미출력() {

        // when & then
        contextRunner
                .withPropertyValues(
                        "shelved.llm.provider=claude",
                        "shelved.llm.api-key=",
                        "shelved.llm.model=example-main-model",
                        "shelved.llm.filter-model=example-filter-model")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("CLAUDE_API_KEY")
                            .hasMessageNotContaining("example-main-model");
                });
    }

    @Test
    void providerClaude_모델비어있음_기동실패() {

        // when & then
        contextRunner
                .withPropertyValues(
                        "shelved.llm.provider=claude",
                        "shelved.llm.api-key=" + FAKE_KEY)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .hasMessageContaining("CLAUDE_MODEL")
                            .hasMessageContaining("CLAUDE_FILTER_MODEL")
                            .hasMessageNotContaining(FAKE_KEY);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(LlmProperties.class)
    static class PropertiesConfiguration {
    }
}
