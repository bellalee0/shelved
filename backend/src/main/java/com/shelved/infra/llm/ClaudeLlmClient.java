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
 * - 분류 호출(FILTER)은 생각(thinking)을 끄고 응답 형식을 {"blocked": boolean}으로 강제한다. 본 응답은 모델 기본값을 쓴다
 */
public class ClaudeLlmClient implements LlmClient {

    public static final String API_BASE_URL = "https://api.anthropic.com";
    private static final String MESSAGES_PATH = "/v1/messages";
    private static final String API_KEY_HEADER = "x-api-key";
    private static final String VERSION_HEADER = "anthropic-version";
    private static final String API_VERSION = "2023-06-01";

    // 분류 응답 {"blocked": true|false} (QuestionFilterResponseParser가 읽는 형식)
    private static final Map<String, Object> FILTER_OUTPUT_FORMAT = Map.of(
            "type", "json_schema",
            "schema", Map.of(
                    "type", "object",
                    "properties", Map.of("blocked", Map.of("type", "boolean")),
                    "required", List.of("blocked"),
                    "additionalProperties", false));

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
        if (request.modelTier() == LlmModelTier.FILTER) {
            // 분류는 JSON 한 줄만 낸다. 분류 모델(Haiku 5.5)은 생각이 기본으로 켜져 있어 짧은 출력 한도를
            // 생각에 다 쓰면 텍스트 없이 max_tokens로 끝나므로 끈다. 생각 끄기를 허용하지 않는 모델로 바꾸면 400이 된다
            body.put("thinking", Map.of("type", "disabled"));
            // "어떻게 질문하면 돼?" 같은 메타 질문에 설명문으로 답하지 않도록 응답 형식을 구조화된 출력으로 강제한다.
            // FILTER 등급은 질문 분류(QuestionFilterService) 전용이다. 다른 용도로 쓰게 되면 이 스키마를 요청 쪽으로 옮긴다
            body.put("output_config", Map.of("format", FILTER_OUTPUT_FORMAT));
        }
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
            // 빈 응답의 원인(거절·출력 없이 종료·토큰 한도)을 구분하도록 stop_reason만 사유에 담는다
            throw LlmException.invalidResponse("텍스트 없음 stop_reason=" + response.stopReason());
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
            @JsonProperty("stop_reason") String stopReason,
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
