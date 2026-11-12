package com.shelved.infra.llm;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * FakeLlmClient 호출 1회의 동작. 정적 팩토리로 만들어 enqueue한다
 */
@FunctionalInterface
public interface FakeLlmScenario {

    LlmResponse run(LlmRequest request, String model);

    /**
     * 정상 응답 (사용량 확인됨)
     */
    static FakeLlmScenario respond(String text) {
        return (request, model) -> new LlmResponse(
                text, FakeLlmClient.DEFAULT_INPUT_TOKENS, FakeLlmClient.DEFAULT_OUTPUT_TOKENS, model);
    }

    /**
     * 정상 응답이지만 사용량을 알 수 없음 (토큰 null)
     */
    static FakeLlmScenario respondWithoutUsage(String text) {
        return (request, model) -> new LlmResponse(text, null, null, model);
    }

    static FakeLlmScenario timeout() {
        return (request, model) -> {
            throw LlmException.timeout();
        };
    }

    static FakeLlmScenario serviceError() {
        return (request, model) -> {
            throw LlmException.serviceError("fake service error");
        };
    }

    /**
     * 응답 본문을 해석할 수 없는 경우. 도메인 형식 불일치는 respond(형식에 맞지 않는 문장)로 재현한다
     */
    static FakeLlmScenario invalidResponse() {
        return (request, model) -> {
            throw LlmException.invalidResponse("fake invalid response");
        };
    }

    /**
     * 일정 시간 기다린 뒤 다음 동작을 실행한다
     */
    static FakeLlmScenario delayed(Duration delay, FakeLlmScenario then) {
        return (request, model) -> {
            sleep(delay);
            return then.run(request, model);
        };
    }

    /**
     * gate.open()이 호출될 때까지 기다린 뒤 다음 동작을 실행한다. 테스트가 AI 호출 중인 시점을 제어할 때 쓴다
     */
    static FakeLlmScenario gated(Gate gate, FakeLlmScenario then) {
        return (request, model) -> {
            gate.enter();
            return then.run(request, model);
        };
    }

    private static void sleep(Duration delay) {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw LlmException.timeout();
        }
    }

    /**
     * 호출을 붙잡아 두는 관문. 최대 대기 시간이 지나도 열리지 않으면 시간 초과로 끝내 테스트가 멈추지 않게 한다
     */
    final class Gate {

        private static final Duration MAX_WAIT = Duration.ofSeconds(30);

        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch opened = new CountDownLatch(1);

        /**
         * 호출이 관문에 도착할 때까지 기다린다. 도착하면 true
         */
        public boolean awaitEntered(Duration timeout) throws InterruptedException {
            return entered.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }

        public void open() {
            opened.countDown();
        }

        private void enter() {
            entered.countDown();
            try {
                if (!opened.await(MAX_WAIT.toMillis(), TimeUnit.MILLISECONDS)) {
                    throw LlmException.timeout();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw LlmException.timeout();
            }
        }
    }
}
