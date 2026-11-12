package com.shelved.infra.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shelved.common.enums.LlmRequestType;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class FakeLlmClientTest {

    private final FakeLlmClient fakeLlmClient = new FakeLlmClient();

    @Test
    void call_시나리오없음_기본문장과사용량() {

        // given
        LlmRequest request = request(LlmRequestType.NPC_CHAT, LlmModelTier.MAIN);

        // when
        LlmResponse response = fakeLlmClient.call(request);

        // then
        assertThat(response.text()).isEqualTo(FakeLlmClient.DEFAULT_TEXT);
        assertThat(response.model()).isEqualTo(FakeLlmClient.MAIN_MODEL);
        assertThat(response.usageKnown()).isTrue();
        assertThat(fakeLlmClient.callCount()).isEqualTo(1);
        assertThat(fakeLlmClient.lastRequest()).isSameAs(request);
    }

    @Test
    void call_유형별기본문장_분류모델() {

        // given
        fakeLlmClient.setDefaultText(LlmRequestType.QUESTION_FILTER, "ALLOW");

        // when
        LlmResponse response = fakeLlmClient.call(request(LlmRequestType.QUESTION_FILTER, LlmModelTier.FILTER));

        // then
        assertThat(response.text()).isEqualTo("ALLOW");
        assertThat(response.model()).isEqualTo(FakeLlmClient.FILTER_MODEL);
    }

    @Test
    void call_시나리오순서대로실행후기본응답() {

        // given
        fakeLlmClient.enqueue(FakeLlmScenario.respond("첫 번째"), FakeLlmScenario.respondWithoutUsage("두 번째"));

        // when
        LlmResponse first = fakeLlmClient.call(request(LlmRequestType.HINT, LlmModelTier.MAIN));
        LlmResponse second = fakeLlmClient.call(request(LlmRequestType.HINT, LlmModelTier.MAIN));
        LlmResponse third = fakeLlmClient.call(request(LlmRequestType.HINT, LlmModelTier.MAIN));

        // then
        assertThat(first.text()).isEqualTo("첫 번째");
        assertThat(second.text()).isEqualTo("두 번째");
        assertThat(second.inputTokens()).isNull();
        assertThat(second.outputTokens()).isNull();
        assertThat(second.usageKnown()).isFalse();
        assertThat(third.text()).isEqualTo(FakeLlmClient.DEFAULT_TEXT);
        assertThat(fakeLlmClient.callCount()).isEqualTo(3);
    }

    @Test
    void call_시간초과_서비스오류_형식오류_구분되는예외() {

        // given
        fakeLlmClient.enqueue(FakeLlmScenario.timeout(), FakeLlmScenario.serviceError(), FakeLlmScenario.invalidResponse());
        LlmRequest request = request(LlmRequestType.NPC_CHAT, LlmModelTier.MAIN);

        // when & then
        assertThatThrownBy(() -> fakeLlmClient.call(request))
                .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.getErrorType()).isEqualTo(LlmErrorType.LLM_TIMEOUT));
        assertThatThrownBy(() -> fakeLlmClient.call(request))
                .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.getErrorType()).isEqualTo(LlmErrorType.LLM_SERVICE_ERROR));
        assertThatThrownBy(() -> fakeLlmClient.call(request))
                .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.getErrorType()).isEqualTo(LlmErrorType.LLM_INVALID_RESPONSE));
        assertThat(fakeLlmClient.callCount()).isEqualTo(3);
    }

    @Test
    void call_지연_지정시간만큼기다린뒤응답() {

        // given
        fakeLlmClient.enqueue(FakeLlmScenario.delayed(Duration.ofMillis(200), FakeLlmScenario.respond("늦은 답")));
        long startNanos = System.nanoTime();

        // when
        LlmResponse response = fakeLlmClient.call(request(LlmRequestType.NPC_CHAT, LlmModelTier.MAIN));

        // then
        assertThat(response.text()).isEqualTo("늦은 답");
        assertThat(Duration.ofNanos(System.nanoTime() - startNanos)).isGreaterThanOrEqualTo(Duration.ofMillis(200));
    }

    @Test
    void call_관문_열릴때까지대기() throws Exception {

        // given
        FakeLlmScenario.Gate gate = new FakeLlmScenario.Gate();
        fakeLlmClient.enqueue(FakeLlmScenario.gated(gate, FakeLlmScenario.respond("관문 뒤 답")));

        // when
        CompletableFuture<LlmResponse> future = CompletableFuture.supplyAsync(
                () -> fakeLlmClient.call(request(LlmRequestType.NPC_CHAT, LlmModelTier.MAIN)));

        // then
        assertThat(gate.awaitEntered(Duration.ofSeconds(5))).isTrue();
        assertThat(future).isNotDone();
        gate.open();
        assertThat(future.get(5, TimeUnit.SECONDS).text()).isEqualTo("관문 뒤 답");
    }

    @Test
    void reset_기록과시나리오초기화() {

        // given
        fakeLlmClient.call(request(LlmRequestType.HINT, LlmModelTier.MAIN));
        fakeLlmClient.enqueue(FakeLlmScenario.timeout());
        fakeLlmClient.setDefaultText(LlmRequestType.NPC_CHAT, "바뀐 기본");

        // when
        fakeLlmClient.reset();
        LlmResponse response = fakeLlmClient.call(request(LlmRequestType.NPC_CHAT, LlmModelTier.MAIN));

        // then
        assertThat(response.text()).isEqualTo(FakeLlmClient.DEFAULT_TEXT);
        assertThat(fakeLlmClient.callCount()).isEqualTo(1);
    }

    @Test
    void toString_프롬프트와메시지내용미포함() {

        // given
        LlmRequest request = new LlmRequest(LlmRequestType.NPC_CHAT, LlmModelTier.MAIN,
                "비밀 시스템 지시", List.of(LlmMessage.user("집사는 9시에 어디 있었나요?")), 100);
        LlmResponse response = new LlmResponse("서재에 있었습니다.", 1, 1, "m");

        // when
        String text = request + " " + response + " " + request.messageList().getFirst();

        // then
        assertThat(text).doesNotContain("비밀 시스템 지시", "집사는", "서재에");
    }

    private static LlmRequest request(LlmRequestType requestType, LlmModelTier modelTier) {
        return new LlmRequest(requestType, modelTier, "가상 사건 「시계탑 저택의 마지막 종소리」의 집사로 답한다.",
                List.of(LlmMessage.user("어젯밤 9시에 서재 근처에 있었나요?")), 200);
    }
}
