package com.shelved.infra.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.shelved.common.config.LlmProperties;
import com.shelved.common.enums.LlmRequestType;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Anthropic Messages API 호출 (shelved.llm.provider=claude일 때만 빈으로 등록된다, LlmClientConfig).
 * - 입력이 max-input-tokens를 넘으면 잘라 보내지 않고 호출 전에 LLM_INPUT_TOO_LARGE로 실패한다
 * - 시간 제한은 RestClient의 읽기 시간 제한으로 적용한다 (게임 호출 game-timeout, 사건 제작 casebuilder-chat-timeout)
 * - 요청·응답 본문과 API 키는 로그·예외 메시지에 남기지 않는다 (응답 오류 본문도 버린다)
 */
public class ClaudeLlmClient implements LlmClient {

    public static final String API_BASE_URL = "https://api.anthropic.com";
    private static final String MESSAGES_PATH = "/v1/messages";
    private static final String API_KEY_HEADER = "x-api-key";
    private static final String VERSION_HEADER = "anthropic-version";
    private static final String API_VERSION = "2023-06-01";

    private final LlmProperties llmProperties;
    private final RestClient gameRestClient;
    private final RestClient casebuilderRestClient;

    /**
     * @param gameRestClient        게임플레이 호출용 (읽기 시간 제한 = game-timeout)
     * @param casebuilderRestClient 관리자 사건 제작 호출용 (읽기 시간 제한 = casebuilder-chat-timeout)
     */
    public ClaudeLlmClient(LlmProperties llmProperties, RestClient gameRestClient, RestClient casebuilderRestClient) {
        this.llmProperties = llmProperties;
        this.gameRestClient = gameRestClient;
        this.casebuilderRestClient = casebuilderRestClient;
    }

    @Override
    public LlmResponse call(LlmRequest request) {

        int estimatedInputTokens = LlmTokenEstimator.estimate(request);
        if (estimatedInputTokens > llmProperties.maxInputTokens()) {
            throw LlmException.inputTooLarge(estimatedInputTokens, llmProperties.maxInputTokens());
        }

        String model = modelOf(request.modelTier());
        Map<String, Object> body = requestBody(request, model);

        MessagesResponse response;
        try {
            response = restClientOf(request.requestType()).post()
                    .uri(MESSAGES_PATH)
                    .header(API_KEY_HEADER, llmProperties.apiKey())
                    .header(VERSION_HEADER, API_VERSION)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(MessagesResponse.class);
        } catch (RestClientResponseException e) {
            // 오류 응답 본문은 입력 일부를 담을 수 있으므로 상태 코드만 남긴다
            throw LlmException.serviceError("HTTP " + e.getStatusCode().value());
        } catch (ResourceAccessException e) {
            if (isTimeout(e)) {
                throw LlmException.timeout();
            }
            throw LlmException.serviceError(e.getClass().getSimpleName());
        } catch (RestClientException e) {
            // 응답 본문 변환 실패
            throw LlmException.invalidResponse(e.getClass().getSimpleName());
        }

        return toLlmResponse(response, model);
    }

    @Override
    public String modelOf(LlmModelTier modelTier) {
        return switch (modelTier) {
            case MAIN -> llmProperties.model();
            case FILTER -> llmProperties.filterModel();
        };
    }

    private Map<String, Object> requestBody(LlmRequest request, String model) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", Math.min(request.maxOutputTokens(), llmProperties.maxOutputTokens()));
        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            body.put("system", request.systemPrompt());
        }
        body.put("messages", request.messageList().stream()
                .map(message -> Map.of(
                        "role", message.role().name().toLowerCase(),
                        "content", message.content()))
                .toList());
        return body;
    }

    private RestClient restClientOf(LlmRequestType requestType) {
        return requestType == LlmRequestType.CASE_GENERATION ? casebuilderRestClient : gameRestClient;
    }

    private static LlmResponse toLlmResponse(MessagesResponse response, String requestedModel) {
        if (response == null || response.content() == null) {
            throw LlmException.invalidResponse("본문 없음");
        }

        String text = response.content().stream()
                .filter(block -> "text".equals(block.type()) && block.text() != null)
                .map(ContentBlock::text)
                .collect(Collectors.joining());
        if (text.isBlank()) {
            throw LlmException.invalidResponse("텍스트 없음");
        }

        Usage usage = response.usage();
        Integer inputTokens = usage == null ? null : usage.inputTokens();
        Integer outputTokens = usage == null ? null : usage.outputTokens();
        String model = response.model() != null ? response.model() : requestedModel;

        return new LlmResponse(text, inputTokens, outputTokens, model);
    }

    private static boolean isTimeout(Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpTimeoutException
                    || cause instanceof SocketTimeoutException
                    || cause instanceof TimeoutException
                    || cause instanceof InterruptedIOException) {
                return true;
            }
        }
        return false;
    }

    // --- Messages API 응답 (필요한 필드만) ---

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MessagesResponse(
            String model,
            List<ContentBlock> content,
            Usage usage
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ContentBlock(
            String type,
            String text
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Usage(
            @JsonProperty("input_tokens") Integer inputTokens,
            @JsonProperty("output_tokens") Integer outputTokens
    ) {
    }
}
