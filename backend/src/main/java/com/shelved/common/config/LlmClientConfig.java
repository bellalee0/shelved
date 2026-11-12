package com.shelved.common.config;

import com.shelved.infra.llm.ClaudeLlmClient;
import com.shelved.infra.llm.FakeLlmClient;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * LLM 클라이언트 선택 (shelved.llm.provider).
 * - fake(기본값): FakeLlmClient. 실제 API를 호출하지 않으며 키가 없어도 기동된다
 * - claude: ClaudeLlmClient. api-key·model·filter-model 중 비어 있는 값이 있으면 기동에 실패한다 (값은 출력하지 않음)
 */
@Slf4j
@Configuration
public class LlmClientConfig {

    private static final String PROVIDER_PREFIX = "shelved.llm";
    private static final String PROVIDER_NAME = "provider";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    @Bean
    @ConditionalOnProperty(prefix = PROVIDER_PREFIX, name = PROVIDER_NAME, havingValue = "fake", matchIfMissing = true)
    public FakeLlmClient fakeLlmClient() {
        log.info("LLM provider=fake: 실제 Claude API를 호출하지 않습니다.");
        return new FakeLlmClient();
    }

    @Bean
    @ConditionalOnProperty(prefix = PROVIDER_PREFIX, name = PROVIDER_NAME, havingValue = "claude")
    public ClaudeLlmClient claudeLlmClient(LlmProperties llmProperties) {
        validateClaudeSettings(llmProperties);
        log.info("LLM provider=claude: 실제 Claude API를 호출합니다.");
        return new ClaudeLlmClient(
                llmProperties,
                restClientWithReadTimeout(llmProperties.gameTimeout()),
                restClientWithReadTimeout(llmProperties.casebuilderChatTimeout()));
    }

    private static RestClient restClientWithReadTimeout(Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);

        return RestClient.builder()
                .baseUrl(ClaudeLlmClient.API_BASE_URL)
                .requestFactory(requestFactory)
                .build();
    }

    // 비어 있는 설정의 이름만 알리고 값은 출력하지 않는다
    private static void validateClaudeSettings(LlmProperties llmProperties) {
        List<String> missingList = new ArrayList<>();
        if (!StringUtils.hasText(llmProperties.apiKey())) {
            missingList.add("CLAUDE_API_KEY (shelved.llm.api-key)");
        }
        if (!StringUtils.hasText(llmProperties.model())) {
            missingList.add("CLAUDE_MODEL (shelved.llm.model)");
        }
        if (!StringUtils.hasText(llmProperties.filterModel())) {
            missingList.add("CLAUDE_FILTER_MODEL (shelved.llm.filter-model)");
        }
        if (!missingList.isEmpty()) {
            throw new IllegalStateException(
                    "shelved.llm.provider=claude이면 다음 설정이 필요합니다: " + String.join(", ", missingList));
        }
    }
}
