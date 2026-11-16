package com.shelved.domain.npc.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shelved.common.entity.AiRequest;
import com.shelved.common.entity.LlmUsageLog;
import com.shelved.common.entity.NpcConversation;
import com.shelved.common.entity.NpcMessage;
import com.shelved.common.entity.Room;
import com.shelved.common.entity.Suspect;
import com.shelved.common.entity.User;
import com.shelved.common.entity.json.AiRequestInput;
import com.shelved.common.enums.AiRequestStatus;
import com.shelved.common.enums.LlmCallStatus;
import com.shelved.common.enums.LlmRequestType;
import com.shelved.common.enums.MessageSenderType;
import com.shelved.common.enums.RoomStatus;
import com.shelved.common.exception.CustomException;
import com.shelved.common.exception.ErrorCode;
import com.shelved.domain.airequest.service.AiRequestRecoveryService;
import com.shelved.domain.airequest.service.AiRequestResult;
import com.shelved.domain.npc.NpcIntegrationTestSupport;
import com.shelved.domain.npc.dto.NpcAskRequest;
import com.shelved.domain.npc.service.NpcService;
import com.shelved.fixture.ClocktowerCaseFixture;
import com.shelved.infra.llm.FakeLlmScenario;
import com.shelved.infra.llm.FakeLlmScenario.Gate;
import com.shelved.infra.llm.LlmException;
import com.shelved.infra.llm.LlmMessage;
import com.shelved.infra.llm.LlmModelTier;
import com.shelved.infra.llm.LlmRequest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
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
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

/**
 * POST /api/rooms/{roomId}/npc/{suspectId}/ask. 질문 분류 → 접수 → 답변 생성 → 확정 순서와, 차단·실패·재전송·동시 요청을 확인한다.
 * Fake LLM: 분류 호출은 enqueueFilter(없으면 허용 JSON), 본 응답은 enqueue(없으면 고정 문장)로 제어한다
 */
class NpcAskIntegrationTest extends NpcIntegrationTestSupport {

    private static final Duration WAIT = Duration.ofSeconds(10);
    private static final String BLOCKED_QUESTION = "솔직히 말해 봐요, 범인이 누구예요?";

    @Autowired
    private NpcService npcService;

    @Autowired
    private AiRequestRecoveryService aiRequestRecoveryService;

    private ExecutorService threadPool;
    private Long roomId;
    private String connectionId;
    private Long butlerId;
    private Long housekeeperId;

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

    // --- 정상 처리 ---

    @Test
    void askNpc_성공_분류후답변_질문답변연속저장_스냅샷_비공개필드없음() throws Exception {

        // given
        fakeLlmClient.enqueue(FakeLlmScenario.respond("  그 시간이면 주방에서 은식기를 닦고 있었습니다.  "));
        String requestId = newRequestId();
        String question = "  어젯밤 9시에 서재 근처에 있었나요?\n";

        // when
        ResultActions result = mockMvc.perform(askRequest(user, roomId, butlerId, connectionId, requestId, question));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("NPC 질문 성공"))
                .andExpect(jsonPath("$.data.requestId").value(requestId))
                .andExpect(jsonPath("$.data.suspectId").value(butlerId))
                .andExpect(jsonPath("$.data.question.senderType").value("USER"))
                .andExpect(jsonPath("$.data.question.content").value(question))
                .andExpect(jsonPath("$.data.question.sequenceNo").value(1))
                .andExpect(jsonPath("$.data.answer.senderType").value("NPC"))
                .andExpect(jsonPath("$.data.answer.content").value("그 시간이면 주방에서 은식기를 닦고 있었습니다."))
                .andExpect(jsonPath("$.data.answer.sequenceNo").value(2));
        JsonNode data = readData(result);
        assertThat(data.propertyNames()).containsExactly("requestId", "suspectId", "question", "answer");
        assertThat(data.get("answer").propertyNames())
                .containsExactly("messageId", "senderType", "content", "sequenceNo", "createdAt");
        assertThat(bodyOf(result)).doesNotContain("persona", "Persona", "culprit", "Culprit", "glossary", "[가상 사건]");

        List<NpcMessage> messageList = findMessageList(roomId, butlerId);
        assertThat(messageList).extracting(NpcMessage::getSenderType, NpcMessage::getSequenceNo, NpcMessage::getContent)
                .containsExactly(
                        tuple(MessageSenderType.USER, 1, question),
                        tuple(MessageSenderType.NPC, 2, "그 시간이면 주방에서 은식기를 닦고 있었습니다."));

        AiRequest aiRequest = findAiRequest(roomId, requestId);
        assertThat(aiRequest.getStatus()).isEqualTo(AiRequestStatus.SUCCEEDED);
        assertThat(aiRequest.getInputJson()).isEqualTo(AiRequestInput.question(question));
        assertThat(aiRequest.getResultRefId()).isEqualTo(messageList.get(1).getId());
        assertThat(aiRequest.getResultJson().get("answer").get("messageId").asLong()).isEqualTo(messageList.get(1).getId());

        List<LlmRequest> llmRequestList = fakeLlmClient.requestList();
        assertThat(llmRequestList).extracting(LlmRequest::modelTier).containsExactly(LlmModelTier.FILTER, LlmModelTier.MAIN);
        assertThat(llmRequestList.get(0).messageList()).containsExactly(LlmMessage.user(question));
        LlmRequest npcRequest = llmRequestList.get(1);
        assertThat(npcRequest.systemPrompt()).contains(sample.butler().getPersonaPrompt(), "\"해리스\"", "- Harris → 해리스 (집사)");
        assertThat(npcRequest.messageList()).containsExactly(LlmMessage.user(question));
    }

    @Test
    void askNpc_두번째질문_이전대화이력포함_sequenceNo연속() throws Exception {

        // given
        fakeLlmClient.enqueue(FakeLlmScenario.respond("주방에 있었습니다."), FakeLlmScenario.respond("아홉 번이었습니다."));
        mockMvc.perform(askRequest(user, roomId, butlerId, connectionId, newRequestId(), QUESTION)).andExpect(status().isOk());

        // when
        ResultActions result = mockMvc.perform(
                askRequest(user, roomId, butlerId, connectionId, newRequestId(), "종소리는 몇 번 울렸나요?"));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.question.sequenceNo").value(3))
                .andExpect(jsonPath("$.data.answer.sequenceNo").value(4));
        assertThat(fakeLlmClient.lastRequest().messageList()).containsExactly(
                LlmMessage.user(QUESTION),
                LlmMessage.assistant("주방에 있었습니다."),
                LlmMessage.user("종소리는 몇 번 울렸나요?"));
    }

    @Test
    void askNpc_대화이력_최근한도개만포함() throws Exception {

        // given: 기존 대화 34개(질문·답변 17쌍). 한도 30 (application.yml shelved.npc.history-message-limit)
        saveConversation(roomId, butlerId, 17);

        // when
        ResultActions result = mockMvc.perform(askRequest(user, roomId, butlerId, connectionId, newRequestId(), QUESTION));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.question.sequenceNo").value(35));
        List<LlmMessage> messageList = fakeLlmClient.lastRequest().messageList();
        assertThat(messageList).hasSize(31);
        assertThat(messageList.getFirst()).isEqualTo(LlmMessage.user("이전 질문 3"));
        assertThat(messageList.get(29)).isEqualTo(LlmMessage.assistant("이전 답변 17"));
        assertThat(messageList.getLast()).isEqualTo(LlmMessage.user(QUESTION));
    }

    // --- 입력·대상·권한·세션 상태 (분류 호출 전 거부) ---

    @Test
    void askNpc_대화불가인물_NPC_NOT_CONVERSABLE_분류호출없음() throws Exception {

        // when
        ResultActions result = mockMvc.perform(
                askRequest(user, roomId, sample.doctor().getId(), connectionId, newRequestId(), QUESTION));

        // then
        expectError(result, ErrorCode.NPC_NOT_CONVERSABLE);
        assertThat(fakeLlmClient.callCount()).isZero();
        assertThat(countAiRequests(roomId)).isZero();
    }

    @Test
    void askNpc_다른사건인물과없는인물_SUSPECT_NOT_FOUND() throws Exception {

        // given
        Long otherCaseSuspectId = transactionTemplate.execute(
                status -> ClocktowerCaseFixture.persistPublished(entityManager, T0).butler().getId());

        // when
        ResultActions otherCase = mockMvc.perform(
                askRequest(user, roomId, otherCaseSuspectId, connectionId, newRequestId(), QUESTION));
        ResultActions missing = mockMvc.perform(
                askRequest(user, roomId, Long.MAX_VALUE, connectionId, newRequestId(), QUESTION));

        // then
        expectError(otherCase, ErrorCode.SUSPECT_NOT_FOUND);
        expectError(missing, ErrorCode.SUSPECT_NOT_FOUND);
        assertThat(fakeLlmClient.callCount()).isZero();
        assertThat(countAiRequests(roomId)).isZero();
    }

    @Test
    void askNpc_다른사용자세션_ROOM_NOT_FOUND() throws Exception {

        // given
        User other = saveUser();

        // when
        ResultActions result = mockMvc.perform(askRequest(other, roomId, butlerId, connectionId, newRequestId(), QUESTION));

        // then
        expectError(result, ErrorCode.ROOM_NOT_FOUND);
        assertThat(fakeLlmClient.callCount()).isZero();
    }

    @Test
    void askNpc_연결헤더없음_CONNECTION_ID_REQUIRED() throws Exception {

        // when
        ResultActions result = mockMvc.perform(askRequest(user, roomId, butlerId, null, newRequestId(), QUESTION));

        // then
        expectError(result, ErrorCode.CONNECTION_ID_REQUIRED);
        assertThat(fakeLlmClient.callCount()).isZero();
    }

    @Test
    void askNpc_일시정지_ROOM_NOT_IN_PROGRESS_분류호출없음() throws Exception {

        // given
        roomLifecycleService.pauseRoom(authUserOf(user), roomId);

        // when
        ResultActions result = mockMvc.perform(askRequest(user, roomId, butlerId, connectionId, newRequestId(), QUESTION));

        // then
        expectError(result, ErrorCode.ROOM_NOT_IN_PROGRESS);
        assertThat(fakeLlmClient.callCount()).isZero();
        assertThat(countAiRequests(roomId)).isZero();
    }

    @Test
    void askNpc_시작전과종료후_ROOM_NOT_IN_PROGRESS() throws Exception {

        // given
        Long readyRoomId = saveReadyRoom(user);
        keepAliveUntil(roomId, T0.plusSeconds(LIMIT_SEC));
        roomLifecycleService.recordHeartbeat(authUserOf(user), roomId, connectionId);
        assertThat(findRoom(roomId).getStatus()).isEqualTo(RoomStatus.EXPIRED);

        // when
        ResultActions ready = mockMvc.perform(askRequest(user, readyRoomId, butlerId, "any", newRequestId(), QUESTION));
        ResultActions ended = mockMvc.perform(askRequest(user, roomId, butlerId, connectionId, newRequestId(), QUESTION));

        // then
        expectError(ready, ErrorCode.ROOM_NOT_IN_PROGRESS);
        expectError(ended, ErrorCode.ROOM_NOT_IN_PROGRESS);
        assertThat(fakeLlmClient.callCount()).isZero();
    }

    @Test
    void askNpc_시간만료_ROOM_TIME_EXPIRED_만료확정_분류호출없음() throws Exception {

        // given: 하트비트는 계속 받았지만 제한시간이 지남
        keepAliveUntil(roomId, T0.plusSeconds(LIMIT_SEC));

        // when
        ResultActions result = mockMvc.perform(askRequest(user, roomId, butlerId, connectionId, newRequestId(), QUESTION));

        // then
        expectError(result, ErrorCode.ROOM_TIME_EXPIRED);
        assertThat(findRoom(roomId).getStatus()).isEqualTo(RoomStatus.EXPIRED);
        assertThat(fakeLlmClient.callCount()).isZero();
        assertThat(countAiRequests(roomId)).isZero();
    }

    @Test
    void askNpc_연결교체_CONNECTION_REPLACED_분류호출없음() throws Exception {

        // given: 일시정지 후 다른 창에서 이어하기 → 새 연결
        roomLifecycleService.pauseRoom(authUserOf(user), roomId);
        roomLifecycleService.resumeRoom(authUserOf(user), roomId);

        // when
        ResultActions result = mockMvc.perform(askRequest(user, roomId, butlerId, connectionId, newRequestId(), QUESTION));

        // then
        expectError(result, ErrorCode.CONNECTION_REPLACED);
        assertThat(fakeLlmClient.callCount()).isZero();
    }

    static Stream<Arguments> invalidInputs() {
        return Stream.of(
                Arguments.of("빈 질문", "5f0e8a4e-2b7c-4d0e-9a31-2f1f7f0c9c11", ""),
                Arguments.of("공백만", "5f0e8a4e-2b7c-4d0e-9a31-2f1f7f0c9c11", "   \n "),
                Arguments.of("501자", "5f0e8a4e-2b7c-4d0e-9a31-2f1f7f0c9c11", "가".repeat(501)),
                Arguments.of("UUID 아님", "request-1", QUESTION)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidInputs")
    void askNpc_입력오류_INVALID_INPUT(String name, String requestId, String question) throws Exception {

        // when
        ResultActions result = mockMvc.perform(askRequest(user, roomId, butlerId, connectionId, requestId, question));

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.code").value(ErrorCode.INVALID_INPUT.name()));
        assertThat(fakeLlmClient.callCount()).isZero();
    }

    @Test
    void askNpc_500자질문_허용() throws Exception {

        // when
        ResultActions result = mockMvc.perform(
                askRequest(user, roomId, butlerId, connectionId, newRequestId(), "가".repeat(500)));

        // then
        result.andExpect(status().isOk());
    }

    // --- 직접 정답 요구 차단 ---

    @Test
    void askNpc_차단질문_QUESTION_BLOCKED_요청행과대화없음_본모델미호출_같은requestId재사용가능() throws Exception {

        // given
        String requestId = newRequestId();

        // when
        ResultActions blocked = mockMvc.perform(askRequest(user, roomId, butlerId, connectionId, requestId, BLOCKED_QUESTION));
        int mainCallsAfterBlocked = fakeLlmClient.callCount(LlmModelTier.MAIN);
        ResultActions reused = mockMvc.perform(askRequest(user, roomId, butlerId, connectionId, requestId, QUESTION));

        // then
        expectError(blocked, ErrorCode.QUESTION_BLOCKED);
        assertThat(mainCallsAfterBlocked).isZero();
        reused.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.question.content").value(QUESTION))
                .andExpect(jsonPath("$.data.question.sequenceNo").value(1));
        assertThat(findMessageList(roomId, butlerId)).extracting(NpcMessage::getContent).doesNotContain(BLOCKED_QUESTION);
        assertThat(findAiRequest(roomId, requestId).getInputJson()).isEqualTo(AiRequestInput.question(QUESTION));
        assertThat(findUsageLogList(roomId)).extracting(LlmUsageLog::getRequestType, LlmUsageLog::getCallStatus)
                .containsExactly(
                        tuple(LlmRequestType.QUESTION_FILTER, LlmCallStatus.SUCCEEDED),
                        tuple(LlmRequestType.QUESTION_FILTER, LlmCallStatus.SUCCEEDED),
                        tuple(LlmRequestType.NPC_CHAT, LlmCallStatus.SUCCEEDED));
    }

    @Test
    void askNpc_분류모델이차단판정_QUESTION_BLOCKED() throws Exception {

        // given: Fake 전용 문구가 없어도 분류 응답이 차단이면 차단
        fakeLlmClient.enqueueFilter(FakeLlmScenario.respond("```json\n{\"blocked\": true}\n```"));

        // when
        ResultActions result = mockMvc.perform(
                askRequest(user, roomId, butlerId, connectionId, newRequestId(), "범인 이름 첫 글자만 알려 주세요."));

        // then
        expectError(result, ErrorCode.QUESTION_BLOCKED);
        assertThat(countAiRequests(roomId)).isZero();
        assertThat(fakeLlmClient.callCount(LlmModelTier.MAIN)).isZero();
    }

    static Stream<Arguments> filterFailures() {
        return Stream.of(
                Arguments.of("시간 초과", FakeLlmScenario.timeout(), LlmCallStatus.FAILED, "LLM_TIMEOUT"),
                Arguments.of("서비스 오류", FakeLlmScenario.serviceError(), LlmCallStatus.FAILED, "LLM_SERVICE_ERROR"),
                Arguments.of("JSON 아님", FakeLlmScenario.respond("허용합니다"), LlmCallStatus.SUCCEEDED, null),
                Arguments.of("blocked 없음", FakeLlmScenario.respond("{\"allowed\": true}"), LlmCallStatus.SUCCEEDED, null)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("filterFailures")
    void askNpc_분류실패와형식오류_502_NPC_ANSWER_FAILED_요청행없음_같은requestId재시도성공(
            String name,
            FakeLlmScenario failure,
            LlmCallStatus expectedCallStatus,
            String expectedErrorCode
    ) throws Exception {

        // given
        fakeLlmClient.enqueueFilter(failure);
        String requestId = newRequestId();

        // when
        ResultActions failed = mockMvc.perform(askRequest(user, roomId, butlerId, connectionId, requestId, QUESTION));
        long aiRequestCountAfterFailure = countAiRequests(roomId);
        ResultActions retried = mockMvc.perform(askRequest(user, roomId, butlerId, connectionId, requestId, QUESTION));

        // then
        expectError(failed, ErrorCode.NPC_ANSWER_FAILED);
        failed.andExpect(jsonPath("$.data.detail.requestId").value(requestId));
        assertThat(aiRequestCountAfterFailure).isZero();
        retried.andExpect(status().isOk());
        assertThat(findAiRequest(roomId, requestId).getExecutionNo()).isEqualTo(1);
        assertThat(findMessageList(roomId, butlerId)).hasSize(2);
        LlmUsageLog filterLog = findUsageLogList(roomId).getFirst();
        assertThat(filterLog.getRequestType()).isEqualTo(LlmRequestType.QUESTION_FILTER);
        assertThat(filterLog.getCallStatus()).isEqualTo(expectedCallStatus);
        assertThat(filterLog.getErrorCode()).isEqualTo(expectedErrorCode);
        assertThat(filterLog.getRoom().getId()).isEqualTo(roomId);
        assertThat(filterLog.getMysteryCase().getId()).isEqualTo(sample.mysteryCase().getId());
    }

    // --- 같은 requestId 재전송 ---

    @Test
    void askNpc_완료된requestId재전송_분류호출없이저장된스냅샷_일시정지중에도_대화중복없음() throws Exception {

        // given
        fakeLlmClient.enqueue(FakeLlmScenario.respond("주방에 있었습니다."));
        String requestId = newRequestId();
        JsonNode first = readData(mockMvc.perform(askRequest(user, roomId, butlerId, connectionId, requestId, QUESTION)));
        roomLifecycleService.pauseRoom(authUserOf(user), roomId);
        int callCountBefore = fakeLlmClient.callCount();

        // when
        ResultActions replayed = mockMvc.perform(askRequest(user, roomId, butlerId, connectionId, requestId, QUESTION));
        ResultActions replayedWithoutHeader = mockMvc.perform(askRequest(user, roomId, butlerId, null, requestId, QUESTION));

        // then
        replayed.andExpect(status().isOk());
        replayedWithoutHeader.andExpect(status().isOk());
        assertThat(readData(replayed)).isEqualTo(first);
        assertThat(readData(replayedWithoutHeader)).isEqualTo(first);
        assertThat(fakeLlmClient.callCount()).isEqualTo(callCountBefore);
        assertThat(findMessageList(roomId, butlerId)).hasSize(2);
        assertThat(findUsageLogList(roomId)).hasSize(2);
    }

    @Test
    void askNpc_처리중인requestId재전송_202_분류호출늘지않음() throws Exception {

        // given
        Gate gate = new Gate();
        fakeLlmClient.enqueue(FakeLlmScenario.gated(gate, FakeLlmScenario.respond("주방에 있었습니다.")));
        String requestId = newRequestId();
        Future<Object> execution = submit(() -> ask(requestId, butlerId, QUESTION));
        assertThat(gate.awaitEntered(WAIT)).isTrue();
        int filterCallsBefore = fakeLlmClient.callCount(LlmModelTier.FILTER);

        // when
        ResultActions processing = mockMvc.perform(askRequest(user, roomId, butlerId, connectionId, requestId, QUESTION));

        // then
        processing.andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value("요청을 처리하고 있습니다"))
                .andExpect(jsonPath("$.data.requestId").value(requestId))
                .andExpect(jsonPath("$.data.type").value("NPC_ASK"))
                .andExpect(jsonPath("$.data.targetId").value(butlerId))
                .andExpect(jsonPath("$.data.status").value("PROCESSING"))
                .andExpect(jsonPath("$.data.input.question").value(QUESTION));
        assertThat(fakeLlmClient.callCount(LlmModelTier.FILTER)).isEqualTo(filterCallsBefore);
        gate.open();
        assertThat(execution.get(WAIT.toSeconds(), TimeUnit.SECONDS)).isInstanceOf(AiRequestResult.Completed.class);
        assertThat(findMessageList(roomId, butlerId)).hasSize(2);
    }

    @Test
    void askNpc_같은requestId에다른질문_AI_REQUEST_ID_REUSED_분류호출없음() throws Exception {

        // given
        String requestId = newRequestId();
        mockMvc.perform(askRequest(user, roomId, butlerId, connectionId, requestId, QUESTION)).andExpect(status().isOk());
        int callCountBefore = fakeLlmClient.callCount();

        // when
        ResultActions otherQuestion = mockMvc.perform(
                askRequest(user, roomId, butlerId, connectionId, requestId, "종소리는 몇 번 울렸나요?"));
        ResultActions otherSuspect = mockMvc.perform(
                askRequest(user, roomId, housekeeperId, connectionId, requestId, QUESTION));

        // then
        expectError(otherQuestion, ErrorCode.AI_REQUEST_ID_REUSED);
        expectError(otherSuspect, ErrorCode.AI_REQUEST_ID_REUSED);
        assertThat(fakeLlmClient.callCount()).isEqualTo(callCountBefore);
    }

    // --- 동시 요청 ---

    @Test
    void askNpc_같은NPC동시질문_하나만처리_AI_REQUEST_IN_PROGRESS() throws Exception {

        // given: 첫 질문이 답변을 기다리는 중
        Gate gate = new Gate();
        fakeLlmClient.enqueue(FakeLlmScenario.gated(gate, FakeLlmScenario.respond("주방에 있었습니다.")));
        String firstRequestId = newRequestId();
        Future<Object> first = submit(() -> ask(firstRequestId, butlerId, QUESTION));
        assertThat(gate.awaitEntered(WAIT)).isTrue();

        // when
        ResultActions second = mockMvc.perform(
                askRequest(user, roomId, butlerId, connectionId, newRequestId(), "종소리는 몇 번 울렸나요?"));
        gate.open();
        Object firstResult = first.get(WAIT.toSeconds(), TimeUnit.SECONDS);

        // then
        expectError(second, ErrorCode.AI_REQUEST_IN_PROGRESS);
        second.andExpect(jsonPath("$.data.detail.requestId").value(firstRequestId));
        assertThat(firstResult).isInstanceOf(AiRequestResult.Completed.class);
        assertThat(findMessageList(roomId, butlerId)).extracting(NpcMessage::getSequenceNo).containsExactly(1, 2);
        assertThat(countAiRequests(roomId)).isEqualTo(1);
    }

    @Test
    void askNpc_같은NPC로동시에보낸두질문_하나만성공() throws Exception {

        // given
        Gate gate = new Gate();
        fakeLlmClient.enqueue(FakeLlmScenario.gated(gate, FakeLlmScenario.respond("주방에 있었습니다.")));

        // when
        List<Future<Object>> futureList = submitTogether(
                () -> ask(newRequestId(), butlerId, QUESTION),
                () -> ask(newRequestId(), butlerId, "종소리는 몇 번 울렸나요?"));
        assertThat(gate.awaitEntered(WAIT)).isTrue();
        Object firstDone = awaitFirstDone(futureList);
        gate.open();
        List<Object> resultList = awaitAll(futureList);

        // then
        assertThat(firstDone).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AI_REQUEST_IN_PROGRESS));
        assertThat(resultList).filteredOn(AiRequestResult.Completed.class::isInstance).hasSize(1);
        assertThat(findMessageList(roomId, butlerId)).extracting(NpcMessage::getSequenceNo).containsExactly(1, 2);
        assertThat(fakeLlmClient.callCount(LlmModelTier.MAIN)).isEqualTo(1);
    }

    @Test
    void askNpc_다른NPC동시질문_둘다성공_대화별sequenceNo연속() throws Exception {

        // given
        Gate firstGate = new Gate();
        Gate secondGate = new Gate();
        fakeLlmClient.enqueue(
                FakeLlmScenario.gated(firstGate, FakeLlmScenario.respond("가상 답변")),
                FakeLlmScenario.gated(secondGate, FakeLlmScenario.respond("가상 답변")));

        // when
        List<Future<Object>> futureList = submitTogether(
                () -> ask(newRequestId(), butlerId, QUESTION),
                () -> ask(newRequestId(), housekeeperId, QUESTION));
        assertThat(firstGate.awaitEntered(WAIT)).isTrue();
        assertThat(secondGate.awaitEntered(WAIT)).isTrue();
        firstGate.open();
        secondGate.open();
        List<Object> resultList = awaitAll(futureList);

        // then
        assertThat(resultList).allSatisfy(result -> assertThat(result).isInstanceOf(AiRequestResult.Completed.class));
        assertThat(findMessageList(roomId, butlerId)).extracting(NpcMessage::getSequenceNo).containsExactly(1, 2);
        assertThat(findMessageList(roomId, housekeeperId)).extracting(NpcMessage::getSequenceNo).containsExactly(1, 2);
    }

    // --- 본 모델 실패·늦은 응답·처리 중 세션 변화 ---

    static Stream<Arguments> mainFailures() {
        return Stream.of(
                Arguments.of("시간 초과", FakeLlmScenario.timeout(), "LLM_TIMEOUT"),
                Arguments.of("서비스 오류", FakeLlmScenario.serviceError(), "LLM_SERVICE_ERROR"),
                Arguments.of("입력 한도 초과", (FakeLlmScenario) (request, model) -> {
                    throw LlmException.inputTooLarge(200000, 150000);
                }, "LLM_INPUT_TOO_LARGE"),
                Arguments.of("빈 답변", FakeLlmScenario.respond("   "), null),
                Arguments.of("과도하게 긴 답변", FakeLlmScenario.respond("가".repeat(2001)), null)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("mainFailures")
    void askNpc_본모델실패_502_대화미저장_같은requestId재시도성공_대화1쌍(
            String name,
            FakeLlmScenario failure,
            String expectedErrorCode
    ) throws Exception {

        // given
        fakeLlmClient.enqueue(failure, FakeLlmScenario.respond("주방에 있었습니다."));
        String requestId = newRequestId();

        // when
        ResultActions failed = mockMvc.perform(askRequest(user, roomId, butlerId, connectionId, requestId, QUESTION));
        List<NpcMessage> messagesAfterFailure = findMessageList(roomId, butlerId);
        AiRequest failedRequest = findAiRequest(roomId, requestId);
        ResultActions retried = mockMvc.perform(askRequest(user, roomId, butlerId, connectionId, requestId, QUESTION));

        // then
        expectError(failed, ErrorCode.NPC_ANSWER_FAILED);
        failed.andExpect(jsonPath("$.data.detail.requestId").value(requestId));
        assertThat(messagesAfterFailure).isEmpty();
        assertThat(failedRequest.getStatus()).isEqualTo(AiRequestStatus.FAILED);
        assertThat(failedRequest.getErrorCode()).isEqualTo(ErrorCode.NPC_ANSWER_FAILED.name());
        retried.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answer.content").value("주방에 있었습니다."));
        assertThat(findMessageList(roomId, butlerId)).extracting(NpcMessage::getSequenceNo).containsExactly(1, 2);
        assertThat(findAiRequest(roomId, requestId).getExecutionNo()).isEqualTo(2);
        List<LlmUsageLog> npcLogs = findUsageLogList(roomId).stream()
                .filter(usageLog -> usageLog.getRequestType() == LlmRequestType.NPC_CHAT)
                .toList();
        assertThat(npcLogs).hasSize(2);
        assertThat(npcLogs.getFirst().getErrorCode()).isEqualTo(expectedErrorCode);
        assertThat(npcLogs.getLast().getCallStatus()).isEqualTo(LlmCallStatus.SUCCEEDED);
    }

    @Test
    void askNpc_이전실행의늦은응답_반영하지않음() throws Exception {

        // given: 1차 실행이 답변을 기다리는 동안 복구로 실패 처리 → 같은 요청 재시도(2차)가 먼저 성공
        Gate firstGate = new Gate();
        fakeLlmClient.enqueue(FakeLlmScenario.gated(firstGate, FakeLlmScenario.respond("1차 실행의 늦은 답")));
        String requestId = newRequestId();
        Future<Object> firstExecution = submit(() -> ask(requestId, butlerId, QUESTION));
        assertThat(firstGate.awaitEntered(WAIT)).isTrue();
        aiRequestRecoveryService.recoverAllProcessingRequests();
        fakeLlmClient.enqueue(FakeLlmScenario.respond("2차 실행의 답"));
        AiRequestResult secondResult = ask(requestId, butlerId, QUESTION);

        // when
        firstGate.open();
        Object lateResult = firstExecution.get(WAIT.toSeconds(), TimeUnit.SECONDS);

        // then
        assertThat(answerOf(secondResult)).isEqualTo("2차 실행의 답");
        assertThat(answerOf(lateResult)).isEqualTo("2차 실행의 답");
        assertThat(findMessageList(roomId, butlerId)).extracting(NpcMessage::getContent)
                .containsExactly(QUESTION, "2차 실행의 답");
    }

    @Test
    void askNpc_답변생성중세션종료_ROOM_ENDED_DURING_REQUEST_대화미저장() throws Exception {

        // given
        Gate gate = new Gate();
        fakeLlmClient.enqueue(FakeLlmScenario.gated(gate, FakeLlmScenario.respond("종료 뒤 도착한 답")));
        String requestId = newRequestId();
        Future<Object> execution = submit(() -> ask(requestId, butlerId, QUESTION));
        assertThat(gate.awaitEntered(WAIT)).isTrue();
        keepAliveUntil(roomId, T0.plusSeconds(LIMIT_SEC));
        roomLifecycleService.recordHeartbeat(authUserOf(user), roomId, connectionId);

        // when
        gate.open();
        Object lateResult = execution.get(WAIT.toSeconds(), TimeUnit.SECONDS);

        // then
        assertThat(lateResult).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ROOM_ENDED_DURING_REQUEST));
        assertThat(findMessageList(roomId, butlerId)).isEmpty();
        assertThat(findAiRequest(roomId, requestId).getErrorCode()).isEqualTo(ErrorCode.ROOM_ENDED_DURING_REQUEST.name());
    }

    @Test
    void askNpc_답변생성중일시정지_정상저장() throws Exception {

        // given
        Gate gate = new Gate();
        fakeLlmClient.enqueue(FakeLlmScenario.gated(gate, FakeLlmScenario.respond("일시정지 중 도착한 답")));
        String requestId = newRequestId();
        Future<Object> execution = submit(() -> ask(requestId, butlerId, QUESTION));
        assertThat(gate.awaitEntered(WAIT)).isTrue();
        roomLifecycleService.pauseRoom(authUserOf(user), roomId);

        // when
        gate.open();
        Object result = execution.get(WAIT.toSeconds(), TimeUnit.SECONDS);

        // then
        assertThat(answerOf(result)).isEqualTo("일시정지 중 도착한 답");
        assertThat(findRoom(roomId).getStatus()).isEqualTo(RoomStatus.PAUSED);
        assertThat(findMessageList(roomId, butlerId)).hasSize(2);
    }

    // --- helpers ---

    private AiRequestResult ask(String requestId, Long suspectId, String question) {
        return npcService.askNpc(authUserOf(user), roomId, suspectId, new NpcAskRequest(requestId, question), connectionId);
    }

    // 질문·답변 pairCount쌍을 바로 저장한다 (sequenceNo 1부터, 질문 n·답변 n)
    private void saveConversation(Long targetRoomId, Long suspectId, int pairCount) {
        transactionTemplate.executeWithoutResult(status -> {
            Room room = entityManager.find(Room.class, targetRoomId);
            NpcConversation conversation = new NpcConversation(room, entityManager.getReference(Suspect.class, suspectId));
            entityManager.persist(conversation);
            LocalDateTime createdAt = T0;
            for (int n = 1; n <= pairCount; n++) {
                entityManager.persist(new NpcMessage(conversation, MessageSenderType.USER, room.getHostUser(),
                        "이전 질문 " + n, n * 2 - 1, createdAt));
                entityManager.persist(new NpcMessage(conversation, MessageSenderType.NPC, null,
                        "이전 답변 " + n, n * 2, createdAt));
            }
        });
    }

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

    private static Object awaitFirstDone(List<Future<Object>> futureList) throws Exception {
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
        return ((AiRequestResult.Completed) result).result().get("answer").get("content").asString();
    }
}
