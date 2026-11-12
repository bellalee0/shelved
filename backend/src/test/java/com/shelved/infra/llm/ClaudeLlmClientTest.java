package com.shelved.infra.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
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

    private static LlmRequest npcRequest(String question) {
        return new LlmRequest(LlmRequestType.NPC_CHAT, LlmModelTier.MAIN,
                "가상 사건 「시계탑 저택의 마지막 종소리」의 집사로 답한다.", List.of(LlmMessage.user(question)), 200);
    }
}
