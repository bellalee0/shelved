package com.shelved.domain.hint.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shelved.common.config.HintProperties;
import com.shelved.common.entity.AiRequest;
import com.shelved.common.entity.EvidenceInspection;
import com.shelved.common.entity.Evidence;
import com.shelved.common.entity.Hint;
import com.shelved.common.entity.LlmUsageLog;
import com.shelved.common.entity.PlayResult;
import com.shelved.common.entity.Room;
import com.shelved.common.entity.User;
import com.shelved.common.entity.json.AiRequestInput;
import com.shelved.common.enums.AiRequestStatus;
import com.shelved.common.enums.Difficulty;
import com.shelved.common.enums.LlmCallStatus;
import com.shelved.common.enums.LlmRequestType;
import com.shelved.common.enums.RoomStatus;
import com.shelved.common.exception.CustomException;
import com.shelved.common.exception.ErrorCode;
import com.shelved.domain.airequest.service.AiRequestRecoveryService;
import com.shelved.domain.airequest.service.AiRequestResult;
import com.shelved.domain.hint.HintIntegrationTestSupport;
import com.shelved.domain.hint.dto.HintCreateRequest;
import com.shelved.domain.hint.repository.HintRepository;
import com.shelved.domain.hint.service.HintService;
import com.shelved.fixture.ClocktowerCaseFixture;
import com.shelved.infra.llm.FakeLlmScenario;
import com.shelved.infra.llm.FakeLlmScenario.Gate;
import com.shelved.infra.llm.LlmException;
import com.shelved.infra.llm.LlmMessage;
import com.shelved.infra.llm.LlmModelTier;
import com.shelved.infra.llm.LlmRequest;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

/**
 * POST /api/rooms/{roomId}/hints. 질문 분류 → 접수(한도·세션당 동시 1건) → 힌트 생성 → 확정(1회 사용) 순서와,
 * 한도·차단·실패·재전송·동시 요청·늦은 응답을 확인한다
 */
class HintCreateIntegrationTest extends HintIntegrationTestSupport {

    @Autowired
    private HintService hintService;

    @Autowired
    private AiRequestRecoveryService aiRequestRecoveryService;

    @Autowired
    private HintRepository hintRepository;

    @Autowired
    private HintProperties hintProperties;

    // --- 정상 처리 ---

    @Test
    void createHint_성공_분류후생성_1회사용_스냅샷_정답정보없음() throws Exception {

        // given
        fakeLlmClient.enqueue(FakeLlmScenario.respond("  마거릿은 9시 15분쯤 목소리를 들었다고 말했습니다. 그 시각을 다시 확인해 보세요.  "));
        String requestId = newRequestId();
        String question = "  " + QUESTION + "\n";

        // when
        ResultActions result = mockMvc.perform(hintRequest(user, roomId, connectionId, requestId, question));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("힌트 요청 성공"))
                .andExpect(jsonPath("$.data.hint.requestId").value(requestId))
                .andExpect(jsonPath("$.data.hint.question").value(question))
                .andExpect(jsonPath("$.data.hint.content").value("마거릿은 9시 15분쯤 목소리를 들었다고 말했습니다. 그 시각을 다시 확인해 보세요."))
                .andExpect(jsonPath("$.data.hint.followup").isEmpty())
                .andExpect(jsonPath("$.data.hint.followupAvailable").value(true))
                .andExpect(jsonPath("$.data.hintCount.limit").value(5))
                .andExpect(jsonPath("$.data.hintCount.used").value(1))
                .andExpect(jsonPath("$.data.hintCount.reserved").value(0))
                .andExpect(jsonPath("$.data.hintCount.remaining").value(4));
        JsonNode data = readData(result);
        assertThat(data.propertyNames()).containsExactly("hint", "hintCount");
        assertThat(data.get("hint").propertyNames())
                .containsExactly("hintId", "requestId", "question", "content", "createdAt", "followup", "followupAvailable");
        assertThat(bodyOf(result)).doesNotContain(ClocktowerCaseFixture.TRICK_SUMMARY, ClocktowerCaseFixture.TRICK_ELEMENT_DESCRIPTION,
                "persona", "culprit", "glossary", "[가상 사건]", "범인:", "태엽 장치");

        List<Hint> hintList = findHintList(roomId);
        assertThat(hintList).singleElement().satisfies(hint -> {
            assertThat(hint.getQuestion()).isEqualTo(question);
            assertThat(hint.getRequestId()).isEqualTo(requestId);
            assertThat(hint.getCreatedAt()).isEqualTo(T0);
        });
        AiRequest aiRequest = findAiRequest(roomId, requestId);
        assertThat(aiRequest.getStatus()).isEqualTo(AiRequestStatus.SUCCEEDED);
        assertThat(aiRequest.getTargetId()).isNull();
        assertThat(aiRequest.getInputJson()).isEqualTo(AiRequestInput.question(question));
        assertThat(aiRequest.getResultRefId()).isEqualTo(hintList.getFirst().getId());
        assertThat(aiRequest.getResultJson()).isEqualTo(data);

        assertThat(fakeLlmClient.requestList()).extracting(LlmRequest::modelTier, LlmRequest::requestType)
                .containsExactly(tuple(LlmModelTier.FILTER, LlmRequestType.QUESTION_FILTER), tuple(LlmModelTier.MAIN, LlmRequestType.HINT));
        assertThat(fakeLlmClient.lastRequest().messageList()).containsExactly(LlmMessage.user(question));
        assertThat(findUsageLogList(roomId)).extracting(LlmUsageLog::getRequestType, LlmUsageLog::getCallStatus)
                .containsExactly(
                        tuple(LlmRequestType.QUESTION_FILTER, LlmCallStatus.SUCCEEDED),
                        tuple(LlmRequestType.HINT, LlmCallStatus.SUCCEEDED));
    }

    @Test
    void createHint_프롬프트_사건정보_정답참고_조사물증_NPC진술출처_이전힌트() throws Exception {

        // given
        inspect(sample.tornMemo());
        saveConversation(roomId, sample.housekeeper().getId(), 1, "가정부");
        saveHints(roomId, 1);

        // when
        mockMvc.perform(hintRequest(user, roomId, connectionId, newRequestId(), QUESTION)).andExpect(status().isOk());

        // then
        String systemPrompt = fakeLlmClient.lastRequest().systemPrompt();
        assertThat(systemPrompt).contains(
                "사건 소개: " + ClocktowerCaseFixture.INTRODUCTION,
                "- Harris → 해리스 (집사)",
                "- 해리스: 저택의 집사",
                "- 서재의 작은 상자: 책장 뒤에서 발견된",
                "범인: 해리스",
                "범행 방법 요약: " + ClocktowerCaseFixture.TRICK_SUMMARY,
                "- " + ClocktowerCaseFixture.TRICK_ELEMENT_DESCRIPTION,
                "### 조사한 물증\n- 찢긴 메모",
                "#### 대화 상대: 마거릿",
                "- 플레이어의 질문: 가정부 질문 1",
                "- 마거릿의 진술: 가정부 답변 1",
                "1. 질문: 이전 질문 1\n   답변: 이전 답변 1");
        assertThat(systemPrompt).doesNotContain(sample.butler().getPersonaPrompt(), sample.housekeeper().getPersonaPrompt(),
                "trk-1-i1", "culprit", "{{");
    }

    @Test
    void createHint_프롬프트_NPC별최근메시지한도와이전힌트한도만포함() throws Exception {

        // given: 마거릿 대화 24개(12쌍), 톰 대화 4개(2쌍), 이전 힌트 0개 → 한도 NPC당 20 (application.yml shelved.hint.npc-message-limit)
        saveConversation(roomId, sample.housekeeper().getId(), 12, "가정부");
        saveConversation(roomId, sample.gardener().getId(), 2, "정원사");

        // when
        mockMvc.perform(hintRequest(user, roomId, connectionId, newRequestId(), QUESTION)).andExpect(status().isOk());

        // then
        String systemPrompt = fakeLlmClient.lastRequest().systemPrompt();
        assertThat(systemPrompt).doesNotContain("가정부 질문 1\n", "가정부 답변 2\n")
                .contains("- 플레이어의 질문: 가정부 질문 3\n", "- 마거릿의 진술: 가정부 답변 12",
                        "- 플레이어의 질문: 정원사 질문 1\n", "- 톰의 진술: 정원사 답변 2");
        assertThat(systemPrompt.split("마거릿의 진술: ", -1)).hasSize(11);
        assertThat(systemPrompt.split("플레이어의 질문: 가정부", -1)).hasSize(11);
        assertThat(systemPrompt.indexOf("#### 대화 상대: 마거릿")).isLessThan(systemPrompt.indexOf("#### 대화 상대: 톰"));
    }

    @Test
    void createHint_프롬프트_이전힌트_오래된순_질문답변쌍() throws Exception {

        // given: 쉬움(5회) 중 4회 사용
        saveHints(roomId, 4);

        // when
        mockMvc.perform(hintRequest(user, roomId, connectionId, newRequestId(), QUESTION)).andExpect(status().isOk());

        // then
        assertThat(fakeLlmClient.lastRequest().systemPrompt()).contains(
                "1. 질문: 이전 질문 1\n   답변: 이전 답변 1\n"
                        + "2. 질문: 이전 질문 2\n   답변: 이전 답변 2\n"
                        + "3. 질문: 이전 질문 3\n   답변: 이전 답변 3\n"
                        + "4. 질문: 이전 질문 4\n   답변: 이전 답변 4");
    }

    @Test
    void findByRoomIdOrderByCreatedAtDescIdDesc_이전힌트조회_최근한도개만() {

        // given: 힌트 12개. 이전 힌트 한도는 application.yml shelved.hint.history-limit (10)
        saveHints(roomId, 12);

        // when
        List<Hint> recentList = hintRepository.findByRoomIdOrderByCreatedAtDescIdDesc(
                roomId, Limit.of(hintProperties.historyLimit()));

        // then
        assertThat(hintProperties.historyLimit()).isEqualTo(10);
        assertThat(recentList).extracting(Hint::getQuestion)
                .containsExactly("이전 질문 12", "이전 질문 11", "이전 질문 10", "이전 질문 9", "이전 질문 8",
                        "이전 질문 7", "이전 질문 6", "이전 질문 5", "이전 질문 4", "이전 질문 3");
    }

    // --- 횟수 한도 ---

    static Stream<Arguments> difficultyLimits() {
        return Stream.of(
                Arguments.of(Difficulty.EASY, 5),
                Arguments.of(Difficulty.NORMAL, 3),
                Arguments.of(Difficulty.HARD, 1)
        );
    }

    @ParameterizedTest(name = "{0} 한도 {1}")
    @MethodSource("difficultyLimits")
    void createHint_난이도별한도소진후_HINT_LIMIT_EXCEEDED_분류호출없음_행없음(Difficulty difficulty, int limit) throws Exception {

        // given
        changeDifficulty(difficulty);
        for (int n = 0; n < limit; n++) {
            mockMvc.perform(hintRequest(user, roomId, connectionId, newRequestId(), QUESTION)).andExpect(status().isOk());
        }
        int callCountBefore = fakeLlmClient.callCount();
        long aiRequestCountBefore = countAiRequests(roomId);

        // when
        ResultActions result = mockMvc.perform(hintRequest(user, roomId, connectionId, newRequestId(), QUESTION));

        // then
        expectError(result, ErrorCode.HINT_LIMIT_EXCEEDED);
        assertThat(findHintList(roomId)).hasSize(limit);
        assertThat(fakeLlmClient.callCount()).isEqualTo(callCountBefore);
        assertThat(countAiRequests(roomId)).isEqualTo(aiRequestCountBefore);
        mockMvc.perform(stateRequest(user, roomId))
                .andExpect(jsonPath("$.data.hintCount.limit").value(limit))
                .andExpect(jsonPath("$.data.hintCount.used").value(limit))
                .andExpect(jsonPath("$.data.hintCount.remaining").value(0));
    }

    @Test
    void createHint_한도직전_처리중확보분포함_다음요청은HINT_LIMIT_EXCEEDED_한도초과없음() throws Exception {

        // given: 보통(3회) 중 2회 사용, 마지막 1회를 처리 중으로 확보
        changeDifficulty(Difficulty.NORMAL);
        saveHints(roomId, 2);
        Gate gate = new Gate();
        fakeLlmClient.enqueue(FakeLlmScenario.gated(gate, FakeLlmScenario.respond("마지막 힌트")));
        Future<Object> processing = submit(() -> create(newRequestId(), QUESTION));
        assertThat(gate.awaitEntered(WAIT)).isTrue();
        int filterCallsBefore = fakeLlmClient.callCount(LlmModelTier.FILTER);

        // when
        ResultActions over = mockMvc.perform(hintRequest(user, roomId, connectionId, newRequestId(), "다른 질문"));
        ResultActions state = mockMvc.perform(stateRequest(user, roomId));
        gate.open();
        Object processed = processing.get(WAIT.toSeconds(), TimeUnit.SECONDS);

        // then
        expectError(over, ErrorCode.HINT_LIMIT_EXCEEDED);
        assertThat(fakeLlmClient.callCount(LlmModelTier.FILTER)).isEqualTo(filterCallsBefore);
        state.andExpect(jsonPath("$.data.hintCount.used").value(2))
                .andExpect(jsonPath("$.data.hintCount.reserved").value(1))
                .andExpect(jsonPath("$.data.hintCount.remaining").value(0));
        assertThat(processed).isInstanceOf(AiRequestResult.Completed.class);
        assertThat(((AiRequestResult.Completed) processed).result().get("hintCount").get("used").asInt()).isEqualTo(3);
        assertThat(((AiRequestResult.Completed) processed).result().get("hintCount").get("reserved").asInt()).isZero();
        assertThat(findHintList(roomId)).hasSize(3);
    }

    @Test
    void createHint_한도1회남은상태에서동시요청_하나만사용_한도초과없음() throws Exception {

        // given: 어려움(1회)
        changeDifficulty(Difficulty.HARD);
        Gate gate = new Gate();
        fakeLlmClient.enqueue(
                FakeLlmScenario.gated(gate, FakeLlmScenario.respond("가상 힌트")),
                FakeLlmScenario.gated(gate, FakeLlmScenario.respond("가상 힌트")));

        // when
        List<Future<Object>> futureList = submitTogether(
                () -> create(newRequestId(), QUESTION),
                () -> create(newRequestId(), "다른 질문"),
                () -> create(newRequestId(), "또 다른 질문"));
        assertThat(gate.awaitEntered(WAIT)).isTrue();
        gate.open();
        List<Object> resultList = awaitAll(futureList);

        // then
        assertThat(resultList).filteredOn(AiRequestResult.Completed.class::isInstance).hasSize(1);
        assertThat(resultList).filteredOn(CustomException.class::isInstance)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .hasSize(2)
                .allMatch(code -> code == ErrorCode.HINT_LIMIT_EXCEEDED || code == ErrorCode.AI_REQUEST_IN_PROGRESS);
        assertThat(findHintList(roomId)).hasSize(1);
        assertThat(fakeLlmClient.callCount(LlmModelTier.MAIN)).isEqualTo(1);
    }

    @Test
    void createHint_한도소진후같은requestId재전송_저장된스냅샷_분류호출없음() throws Exception {

        // given: 어려움(1회)을 다 씀
        changeDifficulty(Difficulty.HARD);
        String requestId = newRequestId();
        JsonNode first = readData(mockMvc.perform(hintRequest(user, roomId, connectionId, requestId, QUESTION))
                .andExpect(status().isOk()));
        roomLifecycleService.pauseRoom(authUserOf(user), roomId);
        int callCountBefore = fakeLlmClient.callCount();

        // when
        ResultActions replayed = mockMvc.perform(hintRequest(user, roomId, connectionId, requestId, QUESTION));
        ResultActions replayedWithoutHeader = mockMvc.perform(hintRequest(user, roomId, null, requestId, QUESTION));

        // then
        replayed.andExpect(status().isOk()).andExpect(jsonPath("$.message").value("힌트 요청 성공"));
        assertThat(readData(replayed)).isEqualTo(first);
        assertThat(readData(replayedWithoutHeader)).isEqualTo(first);
        assertThat(first.get("hintCount").get("remaining").asInt()).isZero();
        assertThat(fakeLlmClient.callCount()).isEqualTo(callCountBefore);
        assertThat(findHintList(roomId)).hasSize(1);
    }

    @Test
    void createHint_한도1에서실패후같은요청재시도_자기실패행을이중계산하지않음_1회만사용() throws Exception {

        // given: 어려움(1회). 첫 실행 실패
        changeDifficulty(Difficulty.HARD);
        fakeLlmClient.enqueue(FakeLlmScenario.serviceError(), FakeLlmScenario.respond("재시도 힌트"));
        String requestId = newRequestId();
        expectError(mockMvc.perform(hintRequest(user, roomId, connectionId, requestId, QUESTION)), ErrorCode.HINT_GENERATION_FAILED);

        // when
        ResultActions retried = mockMvc.perform(hintRequest(user, roomId, connectionId, requestId, QUESTION));
        ResultActions next = mockMvc.perform(hintRequest(user, roomId, connectionId, newRequestId(), QUESTION));

        // then
        retried.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hint.content").value("재시도 힌트"))
                .andExpect(jsonPath("$.data.hintCount.used").value(1))
                .andExpect(jsonPath("$.data.hintCount.reserved").value(0))
                .andExpect(jsonPath("$.data.hintCount.remaining").value(0));
        expectError(next, ErrorCode.HINT_LIMIT_EXCEEDED);
        assertThat(findAiRequest(roomId, requestId).getExecutionNo()).isEqualTo(2);
        assertThat(findHintList(roomId)).hasSize(1);
    }

    @Test
    void createHint_다른요청이한도를쓴뒤실패요청재시도_HINT_LIMIT_EXCEEDED() throws Exception {

        // given: 어려움(1회). A 실패 → B 성공(한도 소진)
        changeDifficulty(Difficulty.HARD);
        fakeLlmClient.enqueue(FakeLlmScenario.timeout(), FakeLlmScenario.respond("B의 힌트"));
        String failedRequestId = newRequestId();
        mockMvc.perform(hintRequest(user, roomId, connectionId, failedRequestId, QUESTION));
        mockMvc.perform(hintRequest(user, roomId, connectionId, newRequestId(), "다른 질문")).andExpect(status().isOk());

        // when
        ResultActions retried = mockMvc.perform(hintRequest(user, roomId, connectionId, failedRequestId, QUESTION));

        // then
        expectError(retried, ErrorCode.HINT_LIMIT_EXCEEDED);
        assertThat(findAiRequest(roomId, failedRequestId).getStatus()).isEqualTo(AiRequestStatus.FAILED);
        assertThat(findHintList(roomId)).hasSize(1);
    }

    // --- 동시 요청 ---

    @Test
    void createHint_세션동시새힌트_하나만처리_AI_REQUEST_IN_PROGRESS() throws Exception {

        // given
        Gate gate = new Gate();
        fakeLlmClient.enqueue(FakeLlmScenario.gated(gate, FakeLlmScenario.respond("가상 힌트")));
        String firstRequestId = newRequestId();
        Future<Object> first = submit(() -> create(firstRequestId, QUESTION));
        assertThat(gate.awaitEntered(WAIT)).isTrue();

        // when
        ResultActions second = mockMvc.perform(hintRequest(user, roomId, connectionId, newRequestId(), "다른 질문"));
        gate.open();
        Object firstResult = first.get(WAIT.toSeconds(), TimeUnit.SECONDS);

        // then
        expectError(second, ErrorCode.AI_REQUEST_IN_PROGRESS);
        second.andExpect(jsonPath("$.data.detail.requestId").value(firstRequestId));
        assertThat(firstResult).isInstanceOf(AiRequestResult.Completed.class);
        assertThat(findHintList(roomId)).hasSize(1);
        assertThat(countAiRequests(roomId)).isEqualTo(1);
    }

    @Test
    void createHint_같은requestId동시전송_중복저장없음_1회만사용() throws Exception {

        // given
        Gate gate = new Gate();
        fakeLlmClient.enqueue(FakeLlmScenario.gated(gate, FakeLlmScenario.respond("가상 힌트")));
        String requestId = newRequestId();

        // when
        List<Future<Object>> futureList = submitTogether(
                () -> create(requestId, QUESTION),
                () -> create(requestId, QUESTION));
        assertThat(gate.awaitEntered(WAIT)).isTrue();
        Object firstDone = awaitFirstDone(futureList);
        gate.open();
        List<Object> resultList = awaitAll(futureList);

        // then
        assertThat(firstDone).isInstanceOf(AiRequestResult.Processing.class);
        assertThat(resultList).filteredOn(AiRequestResult.Completed.class::isInstance).hasSize(1);
        assertThat(findHintList(roomId)).hasSize(1);
        assertThat(countAiRequests(roomId)).isEqualTo(1);
        assertThat(fakeLlmClient.callCount(LlmModelTier.MAIN)).isEqualTo(1);
    }

    @Test
    void createHint_처리중인requestId재전송_202_분류호출늘지않음() throws Exception {

        // given
        Gate gate = new Gate();
        fakeLlmClient.enqueue(FakeLlmScenario.gated(gate, FakeLlmScenario.respond("가상 힌트")));
        String requestId = newRequestId();
        Future<Object> execution = submit(() -> create(requestId, QUESTION));
        assertThat(gate.awaitEntered(WAIT)).isTrue();
        int filterCallsBefore = fakeLlmClient.callCount(LlmModelTier.FILTER);

        // when
        ResultActions processing = mockMvc.perform(hintRequest(user, roomId, connectionId, requestId, QUESTION));

        // then
        processing.andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value("요청을 처리하고 있습니다"))
                .andExpect(jsonPath("$.data.type").value("HINT"))
                .andExpect(jsonPath("$.data.targetId").isEmpty())
                .andExpect(jsonPath("$.data.status").value("PROCESSING"))
                .andExpect(jsonPath("$.data.input.question").value(QUESTION));
        assertThat(fakeLlmClient.callCount(LlmModelTier.FILTER)).isEqualTo(filterCallsBefore);
        gate.open();
        assertThat(execution.get(WAIT.toSeconds(), TimeUnit.SECONDS)).isInstanceOf(AiRequestResult.Completed.class);
    }

    @Test
    void createHint_같은requestId에다른질문_AI_REQUEST_ID_REUSED_분류호출없음() throws Exception {

        // given
        String requestId = newRequestId();
        mockMvc.perform(hintRequest(user, roomId, connectionId, requestId, QUESTION)).andExpect(status().isOk());
        int callCountBefore = fakeLlmClient.callCount();

        // when
        ResultActions result = mockMvc.perform(hintRequest(user, roomId, connectionId, requestId, "다른 질문"));

        // then
        expectError(result, ErrorCode.AI_REQUEST_ID_REUSED);
        assertThat(fakeLlmClient.callCount()).isEqualTo(callCountBefore);
    }

    // --- 분류 ---

    @Test
    void createHint_차단질문_QUESTION_BLOCKED_요청행과힌트없음_차감없음_본모델미호출() throws Exception {

        // given
        String requestId = newRequestId();

        // when
        ResultActions blocked = mockMvc.perform(hintRequest(user, roomId, connectionId, requestId, BLOCKED_QUESTION));

        // then
        expectError(blocked, ErrorCode.QUESTION_BLOCKED);
        assertThat(countAiRequests(roomId)).isZero();
        assertThat(findHintList(roomId)).isEmpty();
        assertThat(fakeLlmClient.callCount(LlmModelTier.MAIN)).isZero();
        mockMvc.perform(stateRequest(user, roomId)).andExpect(jsonPath("$.data.hintCount.used").value(0))
                .andExpect(jsonPath("$.data.hintCount.remaining").value(5));
        assertThat(findUsageLogList(roomId)).extracting(LlmUsageLog::getRequestType)
                .containsExactly(LlmRequestType.QUESTION_FILTER);
    }

    static Stream<Arguments> filterFailures() {
        return Stream.of(
                Arguments.of("시간 초과", FakeLlmScenario.timeout()),
                Arguments.of("서비스 오류", FakeLlmScenario.serviceError()),
                Arguments.of("JSON 아님", FakeLlmScenario.respond("허용합니다"))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("filterFailures")
    void createHint_분류실패_502_HINT_GENERATION_FAILED_요청행없음_차감없음_재시도성공(String name, FakeLlmScenario failure) throws Exception {

        // given
        fakeLlmClient.enqueueFilter(failure);
        String requestId = newRequestId();

        // when
        ResultActions failed = mockMvc.perform(hintRequest(user, roomId, connectionId, requestId, QUESTION));
        long aiRequestCountAfterFailure = countAiRequests(roomId);
        int mainCallsAfterFailure = fakeLlmClient.callCount(LlmModelTier.MAIN);
        ResultActions retried = mockMvc.perform(hintRequest(user, roomId, connectionId, requestId, QUESTION));

        // then
        expectError(failed, ErrorCode.HINT_GENERATION_FAILED);
        failed.andExpect(jsonPath("$.data.detail.requestId").value(requestId));
        assertThat(aiRequestCountAfterFailure).isZero();
        assertThat(mainCallsAfterFailure).isZero();
        retried.andExpect(status().isOk()).andExpect(jsonPath("$.data.hintCount.used").value(1));
        assertThat(findAiRequest(roomId, requestId).getExecutionNo()).isEqualTo(1);
        assertThat(findUsageLogList(roomId).getFirst().getRequestType()).isEqualTo(LlmRequestType.QUESTION_FILTER);
    }

    // --- 본 모델 실패·늦은 응답·처리 중 세션 변화 ---

    static Stream<Arguments> mainFailures() {
        return Stream.of(
                Arguments.of("시간 초과", FakeLlmScenario.timeout(), "LLM_TIMEOUT"),
                Arguments.of("서비스 오류", FakeLlmScenario.serviceError(), "LLM_SERVICE_ERROR"),
                Arguments.of("입력 한도 초과", (FakeLlmScenario) (request, model) -> {
                    throw LlmException.inputTooLarge(200000, 150000);
                }, "LLM_INPUT_TOO_LARGE"),
                Arguments.of("빈 답변", FakeLlmScenario.respond("  \n "), null),
                Arguments.of("과도하게 긴 답변", FakeLlmScenario.respond("가".repeat(2001)), null)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("mainFailures")
    void createHint_본모델실패_502_힌트미저장_차감없음_같은요청재시도성공_1회만사용(
            String name,
            FakeLlmScenario failure,
            String expectedErrorCode
    ) throws Exception {

        // given
        fakeLlmClient.enqueue(failure, FakeLlmScenario.respond("재시도 힌트"));
        String requestId = newRequestId();

        // when
        ResultActions failed = mockMvc.perform(hintRequest(user, roomId, connectionId, requestId, QUESTION));
        List<Hint> hintsAfterFailure = findHintList(roomId);
        ResultActions stateAfterFailure = mockMvc.perform(stateRequest(user, roomId));
        ResultActions retried = mockMvc.perform(hintRequest(user, roomId, connectionId, requestId, QUESTION));

        // then
        expectError(failed, ErrorCode.HINT_GENERATION_FAILED);
        failed.andExpect(jsonPath("$.data.detail.requestId").value(requestId));
        assertThat(hintsAfterFailure).isEmpty();
        stateAfterFailure.andExpect(jsonPath("$.data.hintCount.used").value(0))
                .andExpect(jsonPath("$.data.hintCount.reserved").value(0))
                .andExpect(jsonPath("$.data.hintCount.remaining").value(5));
        retried.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hint.content").value("재시도 힌트"))
                .andExpect(jsonPath("$.data.hintCount.used").value(1));
        assertThat(findHintList(roomId)).hasSize(1);
        assertThat(findAiRequest(roomId, requestId).getExecutionNo()).isEqualTo(2);
        List<LlmUsageLog> hintLogs = findUsageLogList(roomId).stream()
                .filter(usageLog -> usageLog.getRequestType() == LlmRequestType.HINT)
                .toList();
        assertThat(hintLogs).hasSize(2);
        assertThat(hintLogs.getFirst().getErrorCode()).isEqualTo(expectedErrorCode);
        assertThat(hintLogs.getLast().getCallStatus()).isEqualTo(LlmCallStatus.SUCCEEDED);
    }

    @Test
    void createHint_이전실행의늦은응답_반영하지않음() throws Exception {

        // given: 1차 실행이 기다리는 동안 복구로 실패 처리 → 같은 요청 재시도(2차)가 먼저 성공
        Gate firstGate = new Gate();
        fakeLlmClient.enqueue(FakeLlmScenario.gated(firstGate, FakeLlmScenario.respond("1차 실행의 늦은 힌트")));
        String requestId = newRequestId();
        Future<Object> firstExecution = submit(() -> create(requestId, QUESTION));
        assertThat(firstGate.awaitEntered(WAIT)).isTrue();
        aiRequestRecoveryService.recoverAllProcessingRequests();
        fakeLlmClient.enqueue(FakeLlmScenario.respond("2차 실행의 힌트"));
        AiRequestResult secondResult = create(requestId, QUESTION);

        // when
        firstGate.open();
        Object lateResult = firstExecution.get(WAIT.toSeconds(), TimeUnit.SECONDS);

        // then
        assertThat(contentOf(secondResult)).isEqualTo("2차 실행의 힌트");
        assertThat(contentOf(lateResult)).isEqualTo("2차 실행의 힌트");
        assertThat(findHintList(roomId)).extracting(Hint::getContent).containsExactly("2차 실행의 힌트");
    }

    @Test
    void createHint_생성중세션종료_ROOM_ENDED_DURING_REQUEST_차감없음_기록은확정힌트만() throws Exception {

        // given: 1회 사용 후 두 번째 힌트를 기다리는 동안 시간 만료
        saveHints(roomId, 1);
        Gate gate = new Gate();
        fakeLlmClient.enqueue(FakeLlmScenario.gated(gate, FakeLlmScenario.respond("종료 뒤 도착한 힌트")));
        String requestId = newRequestId();
        Future<Object> execution = submit(() -> create(requestId, QUESTION));
        assertThat(gate.awaitEntered(WAIT)).isTrue();
        keepAliveUntil(roomId, T0.plusSeconds(LIMIT_SEC));
        roomLifecycleService.recordHeartbeat(authUserOf(user), roomId, connectionId);

        // when
        gate.open();
        Object lateResult = execution.get(WAIT.toSeconds(), TimeUnit.SECONDS);

        // then
        assertThat(lateResult).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ROOM_ENDED_DURING_REQUEST));
        assertThat(findHintList(roomId)).hasSize(1);
        assertThat(findAiRequest(roomId, requestId).getErrorCode()).isEqualTo(ErrorCode.ROOM_ENDED_DURING_REQUEST.name());
        assertThat(findRoom(roomId).getStatus()).isEqualTo(RoomStatus.EXPIRED);
        assertThat(findPlayResult(roomId).getHintUsedCount()).isEqualTo(1);
    }

    @Test
    void createHint_생성중일시정지_정상저장() throws Exception {

        // given
        Gate gate = new Gate();
        fakeLlmClient.enqueue(FakeLlmScenario.gated(gate, FakeLlmScenario.respond("일시정지 중 도착한 힌트")));
        Future<Object> execution = submit(() -> create(newRequestId(), QUESTION));
        assertThat(gate.awaitEntered(WAIT)).isTrue();
        roomLifecycleService.pauseRoom(authUserOf(user), roomId);

        // when
        gate.open();
        Object result = execution.get(WAIT.toSeconds(), TimeUnit.SECONDS);

        // then
        assertThat(contentOf(result)).isEqualTo("일시정지 중 도착한 힌트");
        assertThat(findRoom(roomId).getStatus()).isEqualTo(RoomStatus.PAUSED);
        assertThat(findHintList(roomId)).hasSize(1);
    }

    // --- 권한·세션 상태 ---

    @Test
    void createHint_다른사용자세션_ROOM_NOT_FOUND_분류호출없음() throws Exception {

        // given
        User other = saveUser();

        // when
        ResultActions result = mockMvc.perform(hintRequest(other, roomId, connectionId, newRequestId(), QUESTION));

        // then
        expectError(result, ErrorCode.ROOM_NOT_FOUND);
        assertThat(fakeLlmClient.callCount()).isZero();
    }

    @Test
    void createHint_연결헤더없음_CONNECTION_ID_REQUIRED() throws Exception {

        // when
        ResultActions result = mockMvc.perform(hintRequest(user, roomId, null, newRequestId(), QUESTION));

        // then
        expectError(result, ErrorCode.CONNECTION_ID_REQUIRED);
        assertThat(fakeLlmClient.callCount()).isZero();
    }

    @Test
    void createHint_일시정지와종료후_ROOM_NOT_IN_PROGRESS_분류호출없음() throws Exception {

        // given
        Long pausedRoomId = saveReadyRoom(user);
        String pausedConnectionId = start(user, pausedRoomId);
        roomLifecycleService.pauseRoom(authUserOf(user), pausedRoomId);
        keepAliveUntil(roomId, T0.plusSeconds(LIMIT_SEC));
        roomLifecycleService.recordHeartbeat(authUserOf(user), roomId, connectionId);

        // when
        ResultActions paused = mockMvc.perform(hintRequest(user, pausedRoomId, pausedConnectionId, newRequestId(), QUESTION));
        ResultActions ended = mockMvc.perform(hintRequest(user, roomId, connectionId, newRequestId(), QUESTION));

        // then
        expectError(paused, ErrorCode.ROOM_NOT_IN_PROGRESS);
        expectError(ended, ErrorCode.ROOM_NOT_IN_PROGRESS);
        assertThat(fakeLlmClient.callCount()).isZero();
    }

    @Test
    void createHint_시간만료_ROOM_TIME_EXPIRED_분류호출없음() throws Exception {

        // given
        keepAliveUntil(roomId, T0.plusSeconds(LIMIT_SEC));

        // when
        ResultActions result = mockMvc.perform(hintRequest(user, roomId, connectionId, newRequestId(), QUESTION));

        // then
        expectError(result, ErrorCode.ROOM_TIME_EXPIRED);
        assertThat(findRoom(roomId).getStatus()).isEqualTo(RoomStatus.EXPIRED);
        assertThat(fakeLlmClient.callCount()).isZero();
    }

    @Test
    void createHint_연결교체_CONNECTION_REPLACED_분류호출없음() throws Exception {

        // given
        roomLifecycleService.pauseRoom(authUserOf(user), roomId);
        roomLifecycleService.resumeRoom(authUserOf(user), roomId);

        // when
        ResultActions result = mockMvc.perform(hintRequest(user, roomId, connectionId, newRequestId(), QUESTION));

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
    void createHint_입력오류_INVALID_INPUT(String name, String requestId, String question) throws Exception {

        // when
        ResultActions result = mockMvc.perform(hintRequest(user, roomId, connectionId, requestId, question));

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.code").value(ErrorCode.INVALID_INPUT.name()));
        assertThat(fakeLlmClient.callCount()).isZero();
    }

    @Test
    void createHint_500자질문_허용() throws Exception {

        // when
        ResultActions result = mockMvc.perform(hintRequest(user, roomId, connectionId, newRequestId(), "가".repeat(500)));

        // then
        result.andExpect(status().isOk());
    }

    // --- helpers ---

    private AiRequestResult create(String requestId, String question) {
        return hintService.createHint(authUserOf(user), roomId, new HintCreateRequest(requestId, question), connectionId);
    }

    private void inspect(Evidence evidence) {
        transactionTemplate.executeWithoutResult(status -> entityManager.persist(new EvidenceInspection(
                entityManager.find(Room.class, roomId), entityManager.getReference(Evidence.class, evidence.getId()), T0)));
    }

    private PlayResult findPlayResult(Long targetRoomId) {
        return transactionTemplate.execute(status -> entityManager.createQuery(
                        "select p from PlayResult p where p.room.id = :roomId", PlayResult.class)
                .setParameter("roomId", targetRoomId)
                .getSingleResult());
    }

    private static String contentOf(Object result) {
        if (result instanceof RuntimeException e) {
            throw new AssertionError("성공 결과가 아닙니다: " + e, e);
        }
        assertThat(result).isInstanceOf(AiRequestResult.Completed.class);
        return ((AiRequestResult.Completed) result).result().get("hint").get("content").asString();
    }
}
