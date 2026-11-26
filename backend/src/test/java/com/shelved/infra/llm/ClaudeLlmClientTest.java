package com.shelved.infra.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.shelved.common.config.LlmProperties;
import com.shelved.common.enums.LlmProvider;
import com.shelved.common.enums.LlmRequestType;
import com.shelved.common.exception.CustomException;
import com.shelved.common.exception.ErrorCode;
import com.shelved.domain.airequest.service.QuestionFilterService;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * 실제 Claude API를 호출하지 않는다. MockRestServiceServer로 요청 형식과 응답 해석·오류 구분만 검증한다
 */
class ClaudeLlmClientTest {

    private static final String API_KEY = "sk-ant-example-not-a-real-key";
    private static final String MESSAGES_URL = ClaudeLlmClient.API_BASE_URL + "/v1/messages";
    private static final int MAX_INPUT_TOKENS = 1000;
    private static final int MAX_OUTPUT_TOKENS = 300;

    private MockRestServiceServer server;
    private ClaudeLlmClient claudeLlmClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(ClaudeLlmClient.API_BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        LlmProperties llmProperties = new LlmProperties(LlmProvider.CLAUDE, API_KEY, "example-main-model", "example-filter-model",
                Duration.ofSeconds(60), Duration.ofSeconds(300), Duration.ofSeconds(60), Duration.ofSeconds(30),
                MAX_INPUT_TOKENS, MAX_OUTPUT_TOKENS);
        claudeLlmClient = new ClaudeLlmClient(llmProperties, restClient, restClient);
    }

    @Test
    void call_성공_텍스트와사용량추출_요청형식() {

        // given
        server.expect(requestTo(MESSAGES_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-api-key", API_KEY))
                .andExpect(header("anthropic-version", "2023-06-01"))
                .andExpect(jsonPath("$.model").value("example-filter-model"))
                .andExpect(jsonPath("$.max_tokens").value(MAX_OUTPUT_TOKENS))
                .andExpect(jsonPath("$.system").value("가상 사건의 질문 분류기다."))
                .andExpect(jsonPath("$.messages[0].role").value("user"))
                .andExpect(jsonPath("$.messages[0].content").value("집사는 어젯밤 9시에 어디 있었나요?"))
                .andRespond(withSuccess("""
                        {"id":"msg_1","type":"message","model":"example-filter-model-20260101",
                         "content":[{"type":"text","text":"AL"},{"type":"text","text":"LOW"}],
                         "stop_reason":"end_turn","usage":{"input_tokens":42,"output_tokens":3}}
                        """, MediaType.APPLICATION_JSON));

        // when
        LlmResponse response = claudeLlmClient.call(new LlmRequest(LlmRequestType.QUESTION_FILTER, LlmModelTier.FILTER,
                "가상 사건의 질문 분류기다.", List.of(LlmMessage.user("집사는 어젯밤 9시에 어디 있었나요?")), 5000));

        // then
        server.verify();
        assertThat(response.text()).isEqualTo("ALLOW");
        assertThat(response.inputTokens()).isEqualTo(42);
        assertThat(response.outputTokens()).isEqualTo(3);
        assertThat(response.model()).isEqualTo("example-filter-model-20260101");
    }

    @Test
    void call_분류요청_요청쪽에서지정한thinking끔과응답형식_본문이이전과같음() {

        // given 분류 스키마·thinking 끄기는 QuestionFilterService가 요청에 담는다. 본문은 클라이언트가 하드코딩하던 때와 같아야 한다
        server.expect(requestTo(MESSAGES_URL))
                .andExpect(content().json("""
                        {
                          "model": "example-filter-model",
                          "max_tokens": 64,
                          "thinking": {"type": "disabled"},
                          "output_config": {
                            "format": {
                              "type": "json_schema",
                              "schema": {
                                "type": "object",
                                "properties": {"blocked": {"type": "boolean"}},
                                "required": ["blocked"],
                                "additionalProperties": false
                              }
                            }
                          },
                          "system": "가상 사건의 질문 분류기다.",
                          "messages": [{"role": "user", "content": "어젯밤 무엇을 했나요?"}]
                        }
                        """, JsonCompareMode.STRICT))
                .andRespond(withSuccess("""
                        {"content":[{"type":"text","text":"{\\"blocked\\": false}"}],"usage":{"input_tokens":5,"output_tokens":5}}
                        """, MediaType.APPLICATION_JSON));

        // when
        claudeLlmClient.call(QuestionFilterService.filterRequest("가상 사건의 질문 분류기다.", "어젯밤 무엇을 했나요?"));

        // then
        server.verify();
    }

    @Test
    void call_스키마없는분류등급요청_thinking과응답형식미지정() {

        // given 클라이언트는 모델 등급만 보고 스키마·thinking을 붙이지 않는다
        server.expect(requestTo(MESSAGES_URL))
                .andExpect(jsonPath("$.thinking").doesNotExist())
                .andExpect(jsonPath("$.output_config").doesNotExist())
                .andRespond(withSuccess("""
                        {"content":[{"type":"text","text":"ALLOW"}],"usage":{"input_tokens":5,"output_tokens":5}}
                        """, MediaType.APPLICATION_JSON));

        // when
        claudeLlmClient.call(new LlmRequest(LlmRequestType.QUESTION_FILTER, LlmModelTier.FILTER,
                "가상 사건의 질문 분류기다.", List.of(LlmMessage.user("어젯밤 무엇을 했나요?")), 64));

        // then
        server.verify();
    }

    @Test
    void call_채점요청_스키마와생각effort를본문에담음() {

        // given
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of("feedback", Map.of("type", "string")),
                "required", List.of("feedback"),
                "additionalProperties", false);
        LlmRequest request = new LlmRequest(LlmRequestType.GRADING, LlmModelTier.MAIN, "가상 사건의 채점자다.",
                List.of(LlmMessage.user("녹음기를 사용했다")), 200)
                .withOutputSchema(schema)
                .withThinking(LlmThinking.ADAPTIVE, LlmEffort.HIGH);
        server.expect(requestTo(MESSAGES_URL))
                .andExpect(jsonPath("$.model").value("example-main-model"))
                .andExpect(jsonPath("$.thinking.type").value("adaptive"))
                .andExpect(jsonPath("$.output_config.effort").value("high"))
                .andExpect(jsonPath("$.output_config.format.type").value("json_schema"))
                .andExpect(jsonPath("$.output_config.format.schema.properties.feedback.type").value("string"))
                .andExpect(jsonPath("$.output_config.format.schema.additionalProperties").value(false))
                .andRespond(withSuccess("""
                        {"content":[{"type":"thinking","thinking":""},{"type":"text","text":"{}"}],"usage":{"input_tokens":5,"output_tokens":5}}
                        """, MediaType.APPLICATION_JSON));

        // when
        LlmResponse response = claudeLlmClient.call(request);

        // then 생각 블록은 버리고 텍스트만 읽는다
        server.verify();
        assertThat(response.text()).isEqualTo("{}");
    }

    @Test
    void call_스키마만지정_모델기본값이면thinking과effort미포함() {

        // given
        LlmRequest request = new LlmRequest(LlmRequestType.GRADING, LlmModelTier.MAIN, "가상 사건의 채점자다.",
                List.of(LlmMessage.user("녹음기를 사용했다")), 200)
                .withOutputSchema(Map.of("type", "object", "additionalProperties", false));
        server.expect(requestTo(MESSAGES_URL))
                .andExpect(jsonPath("$.thinking").doesNotExist())
                .andExpect(jsonPath("$.output_config.effort").doesNotExist())
                .andExpect(jsonPath("$.output_config.format.type").value("json_schema"))
                .andRespond(withSuccess("""
                        {"content":[{"type":"text","text":"{}"}],"usage":{"input_tokens":5,"output_tokens":5}}
                        """, MediaType.APPLICATION_JSON));

        // when
        claudeLlmClient.call(request);

        // then
        server.verify();
    }

    @Test
    void call_본응답요청_thinking과응답형식미지정() {

        // given 본 응답은 모델 기본값(생각 켜짐)을 그대로 쓴다
        server.expect(requestTo(MESSAGES_URL))
                .andExpect(jsonPath("$.model").value("example-main-model"))
                .andExpect(jsonPath("$.thinking").doesNotExist())
                .andExpect(jsonPath("$.output_config").doesNotExist())
                .andRespond(withSuccess("""
                        {"content":[{"type":"text","text":"서재 근처에는 가지 않았습니다."}],"usage":{"input_tokens":5,"output_tokens":5}}
                        """, MediaType.APPLICATION_JSON));

        // when
        claudeLlmClient.call(npcRequest("어젯밤 9시에 서재 근처에 있었나요?"));

        // then
        server.verify();
    }

    @Test
    void call_사용량없음_토큰null() {

        // given
        server.expect(requestTo(MESSAGES_URL)).andRespond(withSuccess("""
                {"content":[{"type":"text","text":"서재 근처에는 가지 않았습니다."}]}
                """, MediaType.APPLICATION_JSON));

        // when
        LlmResponse response = claudeLlmClient.call(npcRequest("어젯밤 9시에 서재 근처에 있었나요?"));

        // then
        assertThat(response.usageKnown()).isFalse();
        assertThat(response.inputTokens()).isNull();
        assertThat(response.outputTokens()).isNull();
        assertThat(response.model()).isEqualTo("example-main-model");
    }

    @Test
    void call_입력한도초과_자르지않고호출전실패() {

        // given: 한글 1글자 = 1토큰으로 추정하므로 한도(1000)를 넘는다
        String longQuestion = "시".repeat(MAX_INPUT_TOKENS + 1);

        // when & then
        assertThatThrownBy(() -> claudeLlmClient.call(npcRequest(longQuestion)))
                .isInstanceOfSatisfying(LlmException.class, e -> {
                    assertThat(e.getErrorType()).isEqualTo(LlmErrorType.LLM_INPUT_TOO_LARGE);
                    assertThat(e.getEstimatedInputTokens()).isGreaterThan(MAX_INPUT_TOKENS);
                    assertThat(e.getLimitTokens()).isEqualTo(MAX_INPUT_TOKENS);
                    CustomException customException = e.toInputTooLargeException();
                    assertThat(customException.getErrorCode()).isEqualTo(ErrorCode.LLM_INPUT_TOO_LARGE);
                    assertThat(customException.getDetail())
                            .isEqualTo(new LlmException.InputTooLargeDetail(e.getEstimatedInputTokens(), MAX_INPUT_TOKENS));
                });
        server.verify();
    }

    @Test
    void call_서버오류_서비스오류_본문미포함() {

        // given
        server.expect(requestTo(MESSAGES_URL)).andRespond(withServerError().body("{\"error\":\"echo: 비밀 프롬프트\"}"));

        // when & then
        assertThatThrownBy(() -> claudeLlmClient.call(npcRequest("어젯밤 9시에 서재 근처에 있었나요?")))
                .isInstanceOfSatisfying(LlmException.class, e -> {
                    assertThat(e.getErrorType()).isEqualTo(LlmErrorType.LLM_SERVICE_ERROR);
                    assertThat(e.getMessage()).doesNotContain("비밀 프롬프트");
                    assertThat(e.getCause()).isNull();
                });
    }

    @Test
    void call_시간초과_LLM_TIMEOUT() {

        // given
        server.expect(requestTo(MESSAGES_URL)).andRespond(withException(new HttpTimeoutException("request timed out")));

        // when & then
        assertThatThrownBy(() -> claudeLlmClient.call(npcRequest("어젯밤 9시에 서재 근처에 있었나요?")))
                .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.getErrorType()).isEqualTo(LlmErrorType.LLM_TIMEOUT));
    }

    @Test
    void call_본문해석불가또는텍스트없음_형식오류() {

        // given
        server.expect(requestTo(MESSAGES_URL)).andRespond(withSuccess("not json", MediaType.APPLICATION_JSON));
        server.expect(requestTo(MESSAGES_URL)).andRespond(withSuccess("""
                {"content":[{"type":"tool_use","id":"t1"}],"usage":{"input_tokens":1,"output_tokens":1}}
                """, MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> claudeLlmClient.call(npcRequest("첫 질문")))
                .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.getErrorType()).isEqualTo(LlmErrorType.LLM_INVALID_RESPONSE));
        assertThatThrownBy(() -> claudeLlmClient.call(npcRequest("둘째 질문")))
                .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.getErrorType()).isEqualTo(LlmErrorType.LLM_INVALID_RESPONSE));
    }

    @Test
    void call_텍스트없음_실패사유에stop_reason포함() {

        // given 빈 content로 끝난 응답 (예: 모델이 출력 없이 종료하거나 거절)
        server.expect(requestTo(MESSAGES_URL)).andRespond(withSuccess("""
                {"content":[],"stop_reason":"refusal","usage":{"input_tokens":12,"output_tokens":0}}
                """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(MESSAGES_URL)).andRespond(withSuccess("""
                {"content":[],"usage":{"input_tokens":12,"output_tokens":0}}
                """, MediaType.APPLICATION_JSON));

        // when & then 원인을 구분할 수 있게 stop_reason을 사유에 담는다 (응답 본문은 담지 않는다)
        assertThatThrownBy(() -> claudeLlmClient.call(npcRequest("첫 질문")))
                .isInstanceOfSatisfying(LlmException.class, e -> {
                    assertThat(e.getErrorType()).isEqualTo(LlmErrorType.LLM_INVALID_RESPONSE);
                    assertThat(e.getMessage()).contains("텍스트 없음").contains("stop_reason=refusal");
                });
        assertThatThrownBy(() -> claudeLlmClient.call(npcRequest("둘째 질문")))
                .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.getMessage()).contains("stop_reason=null"));
    }

    private static LlmRequest npcRequest(String question) {
        return new LlmRequest(LlmRequestType.NPC_CHAT, LlmModelTier.MAIN,
                "가상 사건 「시계탑 저택의 마지막 종소리」의 집사로 답한다.", List.of(LlmMessage.user(question)), 200);
    }
}
