package com.shelved.domain.airequest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.shelved.common.entity.AiRequest;
import com.shelved.common.entity.LlmUsageLog;
import com.shelved.common.entity.json.AiRequestInput;
import com.shelved.common.enums.AiRequestStatus;
import com.shelved.common.enums.AiRequestType;
import com.shelved.common.enums.LlmCallStatus;
import com.shelved.common.enums.LlmRequestType;
import com.shelved.common.enums.RoomStatus;
import com.shelved.common.exception.CustomException;
import com.shelved.common.exception.ErrorCode;
import com.shelved.common.jwt.AuthUser;
import com.shelved.domain.airequest.AiRequestIntegrationTestSupport;
import com.shelved.domain.airequest.dto.AiRequestFailureDetail;
import com.shelved.domain.airequest.dto.AiRequestStatusResponse;
import com.shelved.domain.room.dto.RoomStateResponse;
import com.shelved.domain.room.dto.RoomTimerResponse;
import com.shelved.domain.room.repository.AiRequestRepository;
import com.shelved.infra.llm.FakeLlmClient;
import com.shelved.infra.llm.FakeLlmScenario;
import com.shelved.infra.llm.FakeLlmScenario.Gate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * AI 요청 접수·실행·확정 공통 모듈 (AiRequestExecutor). Fake LLM의 관문(Gate)으로 AI 호출 중인 시점을 제어해
 * 동시 요청·늦은 응답·처리 중 세션 종료/일시정지·복구를 재현한다. 예시는 가상 사건 「시계탑 저택의 마지막 종소리」만 쓴다
 */
class AiRequestExecutorIntegrationTest extends AiRequestIntegrationTestSupport {

    private static final Duration WAIT = Duration.ofSeconds(10);
    private static final String QUESTION = "어젯밤 9시에 서재 근처에 있었나요?";

    @Autowired
    private AiRequestExecutor aiRequestExecutor;

    @Autowired
    private AiRequestRepository aiRequestRepository;

    @Autowired
    private AiRequestRecoveryService aiRequestRecoveryService;

    private ExecutorService threadPool;
    private Long roomId;
    private Long butlerId;
    private Long housekeeperId;
    private String connectionId;

    @BeforeEach
    void setUp() {
        threadPool = Executors.newFixedThreadPool(4);
        roomId = saveReadyRoom(user);
        connectionId = start(user, roomId);
        butlerId = sample.butler().getId();
        housekeeperId = sample.housekeeper().getId();
    }

    @AfterEach
    void tearDown() {
        threadPool.shutdownNow();
    }

    // --- 정상 처리·재전송 ---

    @Test
    void execute_새요청_성공결과를스냅샷으로저장() {

        // given
        TestAiRequestHandler handler = npcHandler(5);
        fakeLlmClient.enqueue(FakeLlmScenario.respond("답변: 9시에는 부엌에 있었습니다."));
        String requestId = newRequestId();

        // when
        AiRequestResult result = ask(requestId, butlerId, QUESTION, handler);

        // then
        assertThat(answerOf(result)).isEqualTo("9시에는 부엌에 있었습니다.");
        AiRequest aiRequest = findAiRequest(roomId, requestId);
        assertThat(aiRequest.getStatus()).isEqualTo(AiRequestStatus.SUCCEEDED);
        assertThat(aiRequest.getExecutionNo()).isEqualTo(1);
        assertThat(aiRequest.getResultJson().get("answer").asString()).isEqualTo("9시에는 부엌에 있었습니다.");
        assertThat(aiRequest.getInputJson()).isEqualTo(AiRequestInput.question(QUESTION));
        assertThat(fakeLlmClient.lastRequest().systemPrompt()).isEqualTo(TestAiRequestHandler.SYSTEM_PROMPT);
        assertThat(handler.completeCount).hasValue(1);
    }

    @Test
    void execute_완료된requestId재전송_다시실행하지않고당시스냅샷_재시작후에도복원() {

        // given: 첫 요청 성공(사용 1회, 진행 중) → 다른 요청 성공(사용 2회) → 일시정지
        TestAiRequestHandler handler = npcHandler(5);
        fakeLlmClient.enqueue(
                FakeLlmScenario.respond("답변: 첫 번째 답"),
                FakeLlmScenario.respond("답변: 두 번째 답"));
        String firstRequestId = newRequestId();
        ask(firstRequestId, butlerId, QUESTION, handler);
        ask(newRequestId(), housekeeperId, "종소리는 몇 번 울렸나요?", handler);
        roomLifecycleService.pauseRoom(authUserOf(user), roomId);
        int callCountBefore = fakeLlmClient.callCount();

        // when: 서버 재시작을 흉내 내 메모리 상태가 없는 새 콜백으로 재전송 (일시정지 중이어도 상태 검사 없이 반환)
        TestAiRequestHandler restartedHandler = npcHandler(5);
        AiRequestResult replayed = ask(firstRequestId, butlerId, QUESTION, restartedHandler);

        // then
        assertThat(replayed).isInstanceOf(AiRequestResult.Completed.class);
        assertThat(answerOf(replayed)).isEqualTo("첫 번째 답");
        assertThat(((AiRequestResult.Completed) replayed).result().get("usedCount").asLong()).isEqualTo(1);
        assertThat(((AiRequestResult.Completed) replayed).result().get("roomStatus").asString()).isEqualTo("IN_PROGRESS");
        assertThat(fakeLlmClient.callCount()).isEqualTo(callCountBefore);
        assertThat(restartedHandler.prepareCount).hasValue(0);
        assertThat(restartedHandler.completeCount).hasValue(0);
        assertThat(findAiRequest(roomId, firstRequestId).getExecutionNo()).isEqualTo(1);
    }

    @Test
    void execute_처리중인requestId재전송_202용처리상태() throws Exception {

        // given
        TestAiRequestHandler handler = npcHandler(5);
        Gate gate = new Gate();
        fakeLlmClient.enqueue(FakeLlmScenario.gated(gate, FakeLlmScenario.respond("답변: 늦게 온 답")));
        String requestId = newRequestId();
        Future<Object> first = submit(() -> ask(requestId, butlerId, QUESTION, handler));
        assertThat(gate.awaitEntered(WAIT)).isTrue();

        // when
        AiRequestResult resent = ask(requestId, butlerId, QUESTION, handler);

        // then
        assertThat(resent).isInstanceOfSatisfying(AiRequestResult.Processing.class, processing -> {
            AiRequestStatusResponse status = processing.status();
            assertThat(status.requestId()).isEqualTo(requestId);
            assertThat(status.status()).isEqualTo(AiRequestStatus.PROCESSING);
            assertThat(status.type()).isEqualTo(AiRequestType.NPC_ASK);
            assertThat(status.targetId()).isEqualTo(butlerId);
            assertThat(status.input()).isEqualTo(AiRequestInput.question(QUESTION));
            assertThat(status.result()).isNull();
            assertThat(status.error()).isNull();
        });
        gate.open();
        assertThat(answerOf(first.get(WAIT.toSeconds(), TimeUnit.SECONDS))).isEqualTo("늦게 온 답");
        assertThat(fakeLlmClient.callCount()).isEqualTo(1);
    }

    // --- 동시성 ---

    @Test
    void execute_같은requestId동시접수_하나만처리() throws Exception {

        // given
        TestAiRequestHandler handler = npcHandler(5);
        Gate gate = new Gate();
        fakeLlmClient.enqueue(FakeLlmScenario.gated(gate, FakeLlmScenario.respond("답변: 한 번만 저장")));
        String requestId = newRequestId();

        // when
        List<Future<Object>> futureList = submitTogether(
                () -> ask(requestId, butlerId, QUESTION, handler),
                () -> ask(requestId, butlerId, QUESTION, handler));
        assertThat(gate.awaitEntered(WAIT)).isTrue();
        Object loser = awaitFirstDone(futureList);
        gate.open();

        // then
        assertThat(loser).isInstanceOf(AiRequestResult.Processing.class);
        for (Future<Object> future : futureList) {
            assertThat(future.get(WAIT.toSeconds(), TimeUnit.SECONDS)).isInstanceOf(AiRequestResult.class);
        }
        assertThat(handler.prepareCount).hasValue(1);
        assertThat(handler.completeCount).hasValue(1);
        assertThat(handler.savedAnswerList).containsExactly("한 번만 저장");
        assertThat(fakeLlmClient.callCount()).isEqualTo(1);
        assertThat(findAiRequest(roomId, requestId).getStatus()).isEqualTo(AiRequestStatus.SUCCEEDED);
    }

    @Test
    void execute_같은대상동시요청_하나만처리하고나머지는AI_REQUEST_IN_PROGRESS() throws Exception {

        // given
        TestAiRequestHandler handler = npcHandler(5);
        Gate gate = new Gate();
        fakeLlmClient.enqueue(
                FakeLlmScenario.gated(gate, FakeLlmScenario.respond("답변: 먼저 온 질문의 답")),
                FakeLlmScenario.gated(gate, FakeLlmScenario.respond("답변: 실행되면 안 됨")));
        String firstRequestId = newRequestId();
        String secondRequestId = newRequestId();

        // when
        List<Future<Object>> futureList = submitTogether(
                () -> ask(firstRequestId, butlerId, QUESTION, handler),
                () -> ask(secondRequestId, butlerId, "종소리를 들었나요?", handler));
        assertThat(gate.awaitEntered(WAIT)).isTrue();
        Object loser = awaitFirstDone(futureList);
        gate.open();
        List<Object> resultList = awaitAll(futureList);

        // then
        assertThat(resultList).filteredOn(AiRequestResult.Completed.class::isInstance).hasSize(1);
        assertThat(loser).isInstanceOfSatisfying(CustomException.class, e -> {
            assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AI_REQUEST_IN_PROGRESS);
            String processingRequestId = ((AiRequestFailureDetail) e.getDetail()).requestId();
            assertThat(processingRequestId).isIn(firstRequestId, secondRequestId);
        });
        long succeededCount = Stream.of(firstRequestId, secondRequestId)
                .map(id -> aiRequestRepository.findByRoomIdAndRequestId(roomId, id))
                .filter(found -> found.isPresent() && found.get().getStatus() == AiRequestStatus.SUCCEEDED)
                .count();
        assertThat(succeededCount).isEqualTo(1);
        assertThat(handler.completeCount).hasValue(1);
        assertThat(fakeLlmClient.callCount()).isEqualTo(1);
    }

    @Test
    void execute_한도1에서다른대상동시요청_한도초과없음() throws Exception {

        // given: 같은 유형 한도 1 (사용 확정 + 처리 중 임시 확보)
        TestAiRequestHandler handler = npcHandler(1);
        Gate gate = new Gate();
        fakeLlmClient.enqueue(
                FakeLlmScenario.gated(gate, FakeLlmScenario.respond("답변: 한도 안의 요청")),
                FakeLlmScenario.gated(gate, FakeLlmScenario.respond("답변: 실행되면 안 됨")));

        // when
        List<Future<Object>> futureList = submitTogether(
                () -> ask(newRequestId(), butlerId, QUESTION, handler),
                () -> ask(newRequestId(), housekeeperId, QUESTION, handler));
        assertThat(gate.awaitEntered(WAIT)).isTrue();
        Object loser = awaitFirstDone(futureList);
        gate.open();
        List<Object> resultList = awaitAll(futureList);

        // then
        assertThat(resultList).filteredOn(AiRequestResult.Completed.class::isInstance).hasSize(1);
        assertThat(loser).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.HINT_LIMIT_EXCEEDED));
        assertThat(aiRequestRepository.countByRoomIdAndRequestTypeAndStatus(roomId, AiRequestType.NPC_ASK, AiRequestStatus.SUCCEEDED))
                .isEqualTo(1);
        assertThat(aiRequestRepository.findByRoomIdOrderByIdAsc(roomId)).hasSize(1);
        assertThat(handler.completeCount).hasValue(1);
    }

    // --- AI 실패·재시도 ---

    static Stream<Arguments> failureScenarios() {
        return Stream.of(
                Arguments.of("시간 초과", FakeLlmScenario.timeout()),
                Arguments.of("서비스 오류", FakeLlmScenario.serviceError()),
                Arguments.of("응답 해석 불가", FakeLlmScenario.invalidResponse()),
                Arguments.of("도메인 형식 불일치", FakeLlmScenario.respond("형식에 맞지 않는 답"))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("failureScenarios")
    void execute_AI실패_FAILED와임시확보해제_재시도시실행번호증가(String name, FakeLlmScenario failure) {

        // given: 한도 1 — 실패가 차감되면 재시도가 한도에 걸린다
        TestAiRequestHandler handler = npcHandler(1);
        fakeLlmClient.enqueue(failure, FakeLlmScenario.respond("답변: 다시 보낸 질문의 답"));
        String requestId = newRequestId();

        // when
        assertThatThrownBy(() -> ask(requestId, butlerId, QUESTION, handler))
                .isInstanceOfSatisfying(CustomException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NPC_ANSWER_FAILED);
                    assertThat(e.getDetail()).isEqualTo(new AiRequestFailureDetail(requestId));
                });
        AiRequest failed = findAiRequest(roomId, requestId);
        AiRequestResult retried = ask(requestId, butlerId, QUESTION, handler);

        // then
        assertThat(failed.getStatus()).isEqualTo(AiRequestStatus.FAILED);
        assertThat(failed.getErrorCode()).isEqualTo(ErrorCode.NPC_ANSWER_FAILED.name());
        assertThat(failed.getExecutionNo()).isEqualTo(1);
        assertThat(answerOf(retried)).isEqualTo("다시 보낸 질문의 답");
        AiRequest succeeded = findAiRequest(roomId, requestId);
        assertThat(succeeded.getStatus()).isEqualTo(AiRequestStatus.SUCCEEDED);
        assertThat(succeeded.getExecutionNo()).isEqualTo(2);
        assertThat(succeeded.getErrorCode()).isNull();
        assertThat(handler.completeCount).hasValue(1);
        assertThat(handler.savedAnswerList).containsExactly("다시 보낸 질문의 답");
    }

    @Test
    void execute_실패와재시도_호출마다사용량기록_미확인사용량은null() {

        // given
        TestAiRequestHandler handler = npcHandler(5);
        fakeLlmClient.enqueue(FakeLlmScenario.timeout(), FakeLlmScenario.respondWithoutUsage("답변: 사용량 없는 답"),
                FakeLlmScenario.respond("답변: 사용량 있는 답"));
        String requestId = newRequestId();
        assertThatThrownBy(() -> ask(requestId, butlerId, QUESTION, handler)).isInstanceOf(CustomException.class);

        // when
        ask(requestId, butlerId, QUESTION, handler);
        ask(newRequestId(), housekeeperId, QUESTION, handler);

        // then
        List<LlmUsageLog> usageLogList = findUsageLogList(roomId);
        assertThat(usageLogList).hasSize(3);
        assertThat(usageLogList).allSatisfy(usageLog -> {
            assertThat(usageLog.getRequestType()).isEqualTo(LlmRequestType.NPC_CHAT);
            assertThat(usageLog.getRoom().getId()).isEqualTo(roomId);
            assertThat(usageLog.getMysteryCase().getId()).isEqualTo(sample.mysteryCase().getId());
            assertThat(usageLog.getModel()).isEqualTo(FakeLlmClient.MAIN_MODEL);
            assertThat(usageLog.getEstimatedCost()).isNull();
        });
        LlmUsageLog timeout = usageLogList.get(0);
        assertThat(timeout.getCallStatus()).isEqualTo(LlmCallStatus.FAILED);
        assertThat(timeout.getErrorCode()).isEqualTo("LLM_TIMEOUT");
        assertThat(timeout.isUsageKnown()).isFalse();
        assertThat(timeout.getInputTokens()).isNull();
        assertThat(timeout.getOutputTokens()).isNull();
        LlmUsageLog unknownUsage = usageLogList.get(1);
        assertThat(unknownUsage.getCallStatus()).isEqualTo(LlmCallStatus.SUCCEEDED);
        assertThat(unknownUsage.isUsageKnown()).isFalse();
        assertThat(unknownUsage.getInputTokens()).isNull();
        LlmUsageLog knownUsage = usageLogList.get(2);
        assertThat(knownUsage.isUsageKnown()).isTrue();
        assertThat(knownUsage.getInputTokens()).isEqualTo(FakeLlmClient.DEFAULT_INPUT_TOKENS);
        assertThat(knownUsage.getOutputTokens()).isEqualTo(FakeLlmClient.DEFAULT_OUTPUT_TOKENS);
    }

    // --- 늦은 응답 ---

    @Test
    void execute_이전실행의늦은성공_반영하지않고최신실행결과로응답() throws Exception {

        // given: 1차 실행이 AI 응답을 기다리는 동안 복구로 실패 처리 → 같은 요청 재시도(2차)가 먼저 성공
        TestAiRequestHandler handler = npcHandler(5);
        Gate firstGate = new Gate();
        fakeLlmClient.enqueue(FakeLlmScenario.gated(firstGate, FakeLlmScenario.respond("답변: 1차 실행의 늦은 답")));
        String requestId = newRequestId();
        Future<Object> firstExecution = submit(() -> ask(requestId, butlerId, QUESTION, handler));
        assertThat(firstGate.awaitEntered(WAIT)).isTrue();
        aiRequestRecoveryService.recoverAllProcessingRequests();
        fakeLlmClient.enqueue(FakeLlmScenario.respond("답변: 2차 실행의 답"));
        AiRequestResult secondResult = ask(requestId, butlerId, QUESTION, handler);

        // when
        firstGate.open();
        Object lateResult = firstExecution.get(WAIT.toSeconds(), TimeUnit.SECONDS);

        // then
        assertThat(answerOf(secondResult)).isEqualTo("2차 실행의 답");
        assertThat(answerOf(lateResult)).isEqualTo("2차 실행의 답");
        assertThat(handler.completeCount).hasValue(1);
        assertThat(handler.savedAnswerList).containsExactly("2차 실행의 답");
        AiRequest aiRequest = findAiRequest(roomId, requestId);
        assertThat(aiRequest.getExecutionNo()).isEqualTo(2);
        assertThat(aiRequest.getResultJson().get("answer").asString()).isEqualTo("2차 실행의 답");
    }

    @Test
    void execute_이전실행의늦은실패_새실행을덮지않음() throws Exception {

        // given: 1차 실행 대기 중 복구 → 2차 실행도 AI 응답 대기 중
        TestAiRequestHandler handler = npcHandler(5);
        Gate firstGate = new Gate();
        Gate secondGate = new Gate();
        fakeLlmClient.enqueue(FakeLlmScenario.gated(firstGate, FakeLlmScenario.timeout()));
        String requestId = newRequestId();
        Future<Object> firstExecution = submit(() -> ask(requestId, butlerId, QUESTION, handler));
        assertThat(firstGate.awaitEntered(WAIT)).isTrue();
        aiRequestRecoveryService.recoverAllProcessingRequests();
        fakeLlmClient.enqueue(FakeLlmScenario.gated(secondGate, FakeLlmScenario.respond("답변: 2차 실행의 답")));
        Future<Object> secondExecution = submit(() -> ask(requestId, butlerId, QUESTION, handler));
        assertThat(secondGate.awaitEntered(WAIT)).isTrue();

        // when: 1차 실행의 시간 초과가 늦게 도착
        firstGate.open();
        Object lateFailure = firstExecution.get(WAIT.toSeconds(), TimeUnit.SECONDS);

        // then: 2차 실행은 PROCESSING 그대로이고, 늦은 실패는 현재 상태(처리 중)로 응답
        assertThat(lateFailure).isInstanceOf(AiRequestResult.Processing.class);
        AiRequest processing = findAiRequest(roomId, requestId);
        assertThat(processing.getStatus()).isEqualTo(AiRequestStatus.PROCESSING);
        assertThat(processing.getExecutionNo()).isEqualTo(2);
        secondGate.open();
        assertThat(answerOf(secondExecution.get(WAIT.toSeconds(), TimeUnit.SECONDS))).isEqualTo("2차 실행의 답");
        assertThat(findAiRequest(roomId, requestId).getStatus()).isEqualTo(AiRequestStatus.SUCCEEDED);
    }

    // --- 처리 중 세션 종료·일시정지 ---

    @Test
    void execute_최종종료후도착한응답_반영하지않고ROOM_ENDED_DURING_REQUEST() throws Exception {

        // given
        TestAiRequestHandler handler = npcHandler(5);
        Gate gate = new Gate();
        fakeLlmClient.enqueue(FakeLlmScenario.gated(gate, FakeLlmScenario.respond("답변: 종료 뒤 도착한 답")));
        String requestId = newRequestId();
        Future<Object> execution = submit(() -> ask(requestId, butlerId, QUESTION, handler));
        assertThat(gate.awaitEntered(WAIT)).isTrue();
        // AI 응답을 기다리는 동안 제한시간 소진 → 하트비트에서 만료 확정
        keepAliveUntil(roomId, T0.plusSeconds(LIMIT_SEC));
        RoomTimerResponse timer = roomLifecycleService.recordHeartbeat(authUserOf(user), roomId, connectionId);
        assertThat(timer.status()).isEqualTo(RoomStatus.EXPIRED);

        // when
        gate.open();
        Object lateResult = execution.get(WAIT.toSeconds(), TimeUnit.SECONDS);

        // then
        assertThat(lateResult).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ROOM_ENDED_DURING_REQUEST));
        AiRequest aiRequest = findAiRequest(roomId, requestId);
        assertThat(aiRequest.getStatus()).isEqualTo(AiRequestStatus.FAILED);
        assertThat(aiRequest.getErrorCode()).isEqualTo(ErrorCode.ROOM_ENDED_DURING_REQUEST.name());
        assertThat(aiRequest.getResultJson()).isNull();
        assertThat(handler.completeCount).hasValue(0);
        assertThat(handler.savedAnswerList).isEmpty();
        assertThat(findUsageLogList(roomId)).singleElement()
                .satisfies(usageLog -> assertThat(usageLog.getCallStatus()).isEqualTo(LlmCallStatus.SUCCEEDED));
    }

    @Test
    void execute_일시정지중도착한응답_정상저장() throws Exception {

        // given
        TestAiRequestHandler handler = npcHandler(5);
        Gate gate = new Gate();
        fakeLlmClient.enqueue(FakeLlmScenario.gated(gate, FakeLlmScenario.respond("답변: 일시정지 중 도착한 답")));
        String requestId = newRequestId();
        Future<Object> execution = submit(() -> ask(requestId, butlerId, QUESTION, handler));
        assertThat(gate.awaitEntered(WAIT)).isTrue();
        roomLifecycleService.pauseRoom(authUserOf(user), roomId);

        // when
        gate.open();
        Object result = execution.get(WAIT.toSeconds(), TimeUnit.SECONDS);

        // then
        assertThat(answerOf(result)).isEqualTo("일시정지 중 도착한 답");
        assertThat(((AiRequestResult.Completed) result).result().get("roomStatus").asString()).isEqualTo("PAUSED");
        assertThat(findAiRequest(roomId, requestId).getStatus()).isEqualTo(AiRequestStatus.SUCCEEDED);
        assertThat(findRoom(roomId).getStatus()).isEqualTo(RoomStatus.PAUSED);
        assertThat(handler.completeCount).hasValue(1);
    }

    @Test
    void execute_AI호출중_하트비트와상태조회가막히지않음() throws Exception {

        // given
        TestAiRequestHandler handler = npcHandler(5);
        Gate gate = new Gate();
        fakeLlmClient.enqueue(FakeLlmScenario.gated(gate, FakeLlmScenario.respond("답변: 오래 걸린 답")));
        String requestId = newRequestId();
        Future<Object> execution = submit(() -> ask(requestId, butlerId, QUESTION, handler));
        assertThat(gate.awaitEntered(WAIT)).isTrue();
        AuthUser authUser = authUserOf(user);

        // when
        RoomTimerResponse timer = assertTimeoutPreemptively(Duration.ofSeconds(5),
                () -> roomLifecycleService.recordHeartbeat(authUser, roomId, connectionId));
        RoomStateResponse state = assertTimeoutPreemptively(Duration.ofSeconds(5),
                () -> roomLifecycleService.getRoomState(authUser, roomId));

        // then
        assertThat(timer.status()).isEqualTo(RoomStatus.IN_PROGRESS);
        assertThat(state.aiRequests()).singleElement().satisfies(status -> {
            assertThat(status.requestId()).isEqualTo(requestId);
            assertThat(status.status()).isEqualTo(AiRequestStatus.PROCESSING);
        });
        gate.open();
        assertThat(answerOf(execution.get(WAIT.toSeconds(), TimeUnit.SECONDS))).isEqualTo("오래 걸린 답");
    }

    // --- requestId 재사용 ---

    @Test
    void execute_같은requestId에다른입력_AI_REQUEST_ID_REUSED() {

        // given
        TestAiRequestHandler handler = npcHandler(5);
        fakeLlmClient.enqueue(FakeLlmScenario.respond("답변: 첫 질문의 답"));
        String requestId = newRequestId();
        ask(requestId, butlerId, QUESTION, handler);

        // when & then
        assertErrorCode(() -> ask(requestId, butlerId, "다른 질문입니다.", handler), ErrorCode.AI_REQUEST_ID_REUSED);
        assertErrorCode(() -> ask(requestId, housekeeperId, QUESTION, handler), ErrorCode.AI_REQUEST_ID_REUSED);
        assertThat(fakeLlmClient.callCount()).isEqualTo(1);
    }

    @Test
    void execute_같은requestId를다른유형에사용_AI_REQUEST_ID_REUSED() {

        // given
        fakeLlmClient.enqueue(FakeLlmScenario.respond("답변: NPC 답"));
        String requestId = newRequestId();
        ask(requestId, butlerId, QUESTION, npcHandler(5));
        TestAiRequestHandler hintHandler = new TestAiRequestHandler(AiRequestType.HINT, aiRequestRepository, 5);

        // when & then
        assertErrorCode(() -> ask(requestId, null, QUESTION, hintHandler), ErrorCode.AI_REQUEST_ID_REUSED);
        assertThat(hintHandler.prepareCount).hasValue(0);
    }

    // --- 복구 ---

    @Test
    void recoverStaleRequests_시간제한과여유가지난PROCESSING만실패로정리_늦은응답미반영() throws Exception {

        // given: game-timeout 60s + margin 60s = 120s
        TestAiRequestHandler handler = npcHandler(5);
        Gate gate = new Gate();
        fakeLlmClient.enqueue(FakeLlmScenario.gated(gate, FakeLlmScenario.respond("답변: 복구 뒤 도착한 답")));
        String requestId = newRequestId();
        Future<Object> execution = submit(() -> ask(requestId, butlerId, QUESTION, handler));
        assertThat(gate.awaitEntered(WAIT)).isTrue();

        // when
        testClock.advance(Duration.ofSeconds(119));
        aiRequestRecoveryService.recoverStaleRequests();
        AiRequestStatus beforeThreshold = findAiRequest(roomId, requestId).getStatus();
        testClock.advance(Duration.ofSeconds(1));
        aiRequestRecoveryService.recoverStaleRequests();
        AiRequest recovered = findAiRequest(roomId, requestId);
        gate.open();
        Object lateResult = execution.get(WAIT.toSeconds(), TimeUnit.SECONDS);

        // then
        assertThat(beforeThreshold).isEqualTo(AiRequestStatus.PROCESSING);
        assertThat(recovered.getStatus()).isEqualTo(AiRequestStatus.FAILED);
        assertThat(recovered.getErrorCode()).isEqualTo(ErrorCode.NPC_ANSWER_FAILED.name());
        assertThat(lateResult).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NPC_ANSWER_FAILED));
        assertThat(handler.completeCount).hasValue(0);
        assertThat(findAiRequest(roomId, requestId).getStatus()).isEqualTo(AiRequestStatus.FAILED);
        assertThat(aiRequestRepository.countByRoomIdAndRequestTypeAndStatus(roomId, AiRequestType.NPC_ASK, AiRequestStatus.PROCESSING))
                .isZero();
    }

    @Test
    void recoverAllProcessingRequests_시작시남은PROCESSING을유형별실패코드로정리() {

        // given: 이전 프로세스에서 끝나지 못한 요청 (방금 접수된 것도 포함)
        String npcRequestId = saveProcessing(AiRequestType.NPC_ASK, butlerId);
        String hintRequestId = saveProcessing(AiRequestType.HINT, null);

        // when
        aiRequestRecoveryService.recoverAllProcessingRequests();

        // then
        AiRequest npc = findAiRequest(roomId, npcRequestId);
        AiRequest hint = findAiRequest(roomId, hintRequestId);
        assertThat(npc.getStatus()).isEqualTo(AiRequestStatus.FAILED);
        assertThat(npc.getErrorCode()).isEqualTo(ErrorCode.NPC_ANSWER_FAILED.name());
        assertThat(hint.getStatus()).isEqualTo(AiRequestStatus.FAILED);
        assertThat(hint.getErrorCode()).isEqualTo(ErrorCode.HINT_GENERATION_FAILED.name());
    }

    // --- helpers ---

    private TestAiRequestHandler npcHandler(int limit) {
        return new TestAiRequestHandler(AiRequestType.NPC_ASK, aiRequestRepository, limit);
    }

    private AiRequestResult ask(String requestId, Long targetId, String question, TestAiRequestHandler handler) {
        AiRequestCommand command = new AiRequestCommand(roomId, requestId, targetId, AiRequestInput.question(question), connectionId);
        return aiRequestExecutor.execute(authUserOf(user), command, handler);
    }

    private String saveProcessing(AiRequestType type, Long targetId) {
        String requestId = newRequestId();
        transactionTemplate.executeWithoutResult(status -> entityManager.persist(new AiRequest(
                entityManager.getReference(com.shelved.common.entity.Room.class, roomId),
                requestId, type, targetId, AiRequestInput.question(QUESTION), T0)));
        return requestId;
    }

    // 결과 또는 예외를 값으로 돌려준다
    private Future<Object> submit(Callable<Object> task) {
        return threadPool.submit(() -> {
            try {
                return task.call();
            } catch (RuntimeException e) {
                return e;
            }
        });
    }

    @SafeVarargs
    private List<Future<Object>> submitTogether(Callable<Object>... tasks) throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(tasks.length);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Object>> futureList = new ArrayList<>();
        for (Callable<Object> task : tasks) {
            futureList.add(submit(() -> {
                ready.countDown();
                go.await();
                return task.call();
            }));
        }
        ready.await();
        go.countDown();
        return futureList;
    }

    private static List<Object> awaitAll(List<Future<Object>> futureList) throws Exception {
        List<Object> resultList = new ArrayList<>();
        for (Future<Object> future : futureList) {
            resultList.add(future.get(WAIT.toSeconds(), TimeUnit.SECONDS));
        }
        return resultList;
    }

    // 관문에 걸리지 않은 쪽(먼저 끝나는 쪽)의 결과
    private static Object awaitFirstDone(List<Future<Object>> futureList) throws InterruptedException, ExecutionException {
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (System.nanoTime() < deadline) {
            for (Future<Object> future : futureList) {
                if (future.isDone()) {
                    return future.get();
                }
            }
            Thread.sleep(10);
        }
        throw new AssertionError("먼저 끝나는 요청이 없습니다.");
    }

    private static String answerOf(Object result) {
        if (result instanceof RuntimeException e) {
            throw new AssertionError("성공 결과가 아닙니다: " + e, e);
        }
        assertThat(result).isInstanceOf(AiRequestResult.Completed.class);
        return ((AiRequestResult.Completed) result).result().get("answer").asString();
    }

    private static void assertErrorCode(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getErrorCode()).isEqualTo(errorCode));
    }
}
