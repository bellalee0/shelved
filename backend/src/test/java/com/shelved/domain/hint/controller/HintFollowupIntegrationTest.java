package com.shelved.domain.hint.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shelved.common.entity.HintFollowup;
import com.shelved.common.entity.LlmUsageLog;
import com.shelved.common.entity.User;
import com.shelved.common.entity.json.AiRequestInput;
import com.shelved.common.enums.AiRequestStatus;
import com.shelved.common.enums.Difficulty;
import com.shelved.common.enums.LlmCallStatus;
import com.shelved.common.enums.LlmRequestType;
import com.shelved.common.exception.CustomException;
import com.shelved.common.exception.ErrorCode;
import com.shelved.domain.airequest.service.AiRequestResult;
import com.shelved.domain.hint.HintIntegrationTestSupport;
import com.shelved.domain.hint.dto.HintCreateRequest;
import com.shelved.domain.hint.dto.HintFollowupRequest;
import com.shelved.domain.hint.service.HintService;
import com.shelved.fixture.ClocktowerCaseFixture;
import com.shelved.infra.llm.FakeLlmScenario;
import com.shelved.infra.llm.FakeLlmScenario.Gate;
import com.shelved.infra.llm.LlmMessage;
import com.shelved.infra.llm.LlmModelTier;
import com.shelved.infra.llm.LlmRequest;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

/**
 * POST /api/rooms/{roomId}/hints/{hintId}/followup. 힌트당 1회·힌트 횟수 미차감·힌트별 동시 1건과, 차단·실패 시 미사용을 확인한다
 */
class HintFollowupIntegrationTest extends HintIntegrationTestSupport {

    private static final String HINT_ANSWER = "마거릿은 9시 15분쯤 목소리를 들었다고 말했습니다. 그 시각을 뒷받침하는 다른 진술이 있는지 비교해 보세요.";

    @Autowired
    private HintService hintService;

    // --- 정상 처리 ---

    @Test
    void createHintFollowup_힌트횟수0에서도성공_힌트횟수불변_스냅샷_프롬프트에정답없음() throws Exception {

        // given: 어려움(1회)을 다 쓴 상태
        changeDifficulty(Difficulty.HARD);
        saveConversation(roomId, sample.butler().getId(), 1, "집사");
        Long hintId = createHintOk(HINT_ANSWER);
        fakeLlmClient.enqueue(FakeLlmScenario.respond("  마거릿의 말 말고도 그 시각을 확인해 줄 다른 사람의 말이 있는지 보라는 뜻입니다.  "));
        String requestId = newRequestId();

        // when
        ResultActions result = mockMvc.perform(followupRequest(user, roomId, hintId, connectionId, requestId, FOLLOWUP_QUESTION));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("보충 질문 성공"))
                .andExpect(jsonPath("$.data.hintId").value(hintId))
                .andExpect(jsonPath("$.data.requestId").value(requestId))
                .andExpect(jsonPath("$.data.question").value(FOLLOWUP_QUESTION))
                .andExpect(jsonPath("$.data.content").value("마거릿의 말 말고도 그 시각을 확인해 줄 다른 사람의 말이 있는지 보라는 뜻입니다."));
        JsonNode data = readData(result);
        assertThat(data.propertyNames()).containsExactly("hintId", "requestId", "question", "content", "createdAt");
        mockMvc.perform(stateRequest(user, roomId))
                .andExpect(jsonPath("$.data.hintCount.used").value(1))
                .andExpect(jsonPath("$.data.hintCount.remaining").value(0));
        assertThat(findFollowupList(hintId)).singleElement().satisfies(followup -> {
            assertThat(followup.getRequestId()).isEqualTo(requestId);
            assertThat(followup.getQuestion()).isEqualTo(FOLLOWUP_QUESTION);
        });
        assertThat(findAiRequest(roomId, requestId)).satisfies(aiRequest -> {
            assertThat(aiRequest.getStatus()).isEqualTo(AiRequestStatus.SUCCEEDED);
            assertThat(aiRequest.getTargetId()).isEqualTo(hintId);
            assertThat(aiRequest.getInputJson()).isEqualTo(AiRequestInput.question(FOLLOWUP_QUESTION));
            assertThat(aiRequest.getResultJson()).isEqualTo(data);
        });

        LlmRequest followupLlmRequest = fakeLlmClient.lastRequest();
        assertThat(followupLlmRequest.requestType()).isEqualTo(LlmRequestType.HINT_FOLLOWUP);
        assertThat(followupLlmRequest.messageList()).containsExactly(LlmMessage.user(FOLLOWUP_QUESTION));
        assertThat(followupLlmRequest.systemPrompt())
                .contains("## 원래 질문\n" + QUESTION, "## 힌트 답변\n" + HINT_ANSWER, "- Harris → 해리스 (집사)")
                .doesNotContain(ClocktowerCaseFixture.TRICK_SUMMARY, ClocktowerCaseFixture.TRICK_ELEMENT_DESCRIPTION,
                        "범인: ", sample.butler().getPersonaPrompt(), "태엽 장치", "집사 답변 1", "집사 질문 1",
                        ClocktowerCaseFixture.INTRODUCTION);
        assertThat(findUsageLogList(roomId)).extracting(LlmUsageLog::getRequestType, LlmUsageLog::getCallStatus)
                .containsExactly(
                        tuple(LlmRequestType.QUESTION_FILTER, LlmCallStatus.SUCCEEDED),
                        tuple(LlmRequestType.HINT, LlmCallStatus.SUCCEEDED),
                        tuple(LlmRequestType.QUESTION_FILTER, LlmCallStatus.SUCCEEDED),
                        tuple(LlmRequestType.HINT_FOLLOWUP, LlmCallStatus.SUCCEEDED));
    }

    @Test
    void createHintFollowup_새정보요구에대한안내답변도_사용처리_두번째는HINT_FOLLOWUP_ALREADY_USED() throws Exception {

        // given
        Long hintId = createHintOk(HINT_ANSWER);
        fakeLlmClient.enqueue(FakeLlmScenario.respond("그 부분은 이 힌트의 범위를 벗어나서, 새 힌트가 필요합니다."));
        mockMvc.perform(followupRequest(user, roomId, hintId, connectionId, newRequestId(), "그럼 정원사 톰은 의심해야 하나요?"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("그 부분은 이 힌트의 범위를 벗어나서, 새 힌트가 필요합니다."));
        int callCountBefore = fakeLlmClient.callCount();

        // when
        ResultActions second = mockMvc.perform(followupRequest(user, roomId, hintId, connectionId, newRequestId(), FOLLOWUP_QUESTION));

        // then
        expectError(second, ErrorCode.HINT_FOLLOWUP_ALREADY_USED);
        assertThat(fakeLlmClient.callCount()).isEqualTo(callCountBefore);
        assertThat(findFollowupList(hintId)).hasSize(1);
        mockMvc.perform(hintListRequest(user, roomId))
                .andExpect(jsonPath("$.data.hints[0].followupAvailable").value(false))
                .andExpect(jsonPath("$.data.hints[0].followup.content").value("그 부분은 이 힌트의 범위를 벗어나서, 새 힌트가 필요합니다."));
    }

    @Test
    void createHintFollowup_완료된requestId재전송_저장된스냅샷_분류호출없음() throws Exception {

        // given
        Long hintId = createHintOk(HINT_ANSWER);
        String requestId = newRequestId();
        JsonNode first = readData(mockMvc.perform(followupRequest(user, roomId, hintId, connectionId, requestId, FOLLOWUP_QUESTION))
                .andExpect(status().isOk()));
        roomLifecycleService.pauseRoom(authUserOf(user), roomId);
        int callCountBefore = fakeLlmClient.callCount();

        // when
        ResultActions replayed = mockMvc.perform(followupRequest(user, roomId, hintId, null, requestId, FOLLOWUP_QUESTION));

        // then
        replayed.andExpect(status().isOk());
        assertThat(readData(replayed)).isEqualTo(first);
        assertThat(fakeLlmClient.callCount()).isEqualTo(callCountBefore);
        assertThat(findFollowupList(hintId)).hasSize(1);
    }

    @Test
    void createHintFollowup_같은requestId를다른힌트에_AI_REQUEST_ID_REUSED() throws Exception {

        // given
        List<Long> hintIdList = saveHints(roomId, 2);
        String requestId = newRequestId();
        mockMvc.perform(followupRequest(user, roomId, hintIdList.get(0), connectionId, requestId, FOLLOWUP_QUESTION))
                .andExpect(status().isOk());

        // when
        ResultActions result = mockMvc.perform(followupRequest(user, roomId, hintIdList.get(1), connectionId, requestId, FOLLOWUP_QUESTION));

        // then
        expectError(result, ErrorCode.AI_REQUEST_ID_REUSED);
    }

    // --- 동시 요청 ---

    @Test
    void createHintFollowup_같은힌트동시두건_하나만사용() throws Exception {

        // given
        Long hintId = saveHints(roomId, 1).getFirst();
        Gate gate = new Gate();
        fakeLlmClient.enqueue(
                FakeLlmScenario.gated(gate, FakeLlmScenario.respond("가상 보충 설명")),
                FakeLlmScenario.gated(gate, FakeLlmScenario.respond("가상 보충 설명")));

        // when
        List<Future<Object>> futureList = submitTogether(
                () -> followup(hintId, newRequestId(), FOLLOWUP_QUESTION),
                () -> followup(hintId, newRequestId(), "다른 보충 질문"));
        assertThat(gate.awaitEntered(WAIT)).isTrue();
        Object firstDone = awaitFirstDone(futureList);
        gate.open();
        List<Object> resultList = awaitAll(futureList);

        // then
        assertThat(firstDone).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getErrorCode()).isIn(ErrorCode.AI_REQUEST_IN_PROGRESS, ErrorCode.HINT_FOLLOWUP_ALREADY_USED));
        assertThat(resultList).filteredOn(AiRequestResult.Completed.class::isInstance).hasSize(1);
        assertThat(findFollowupList(hintId)).hasSize(1);
        assertThat(fakeLlmClient.callCount(LlmModelTier.MAIN)).isEqualTo(1);
    }

    @Test
    void createHintFollowup_같은힌트처리중이면AI_REQUEST_IN_PROGRESS_다른힌트보충은동시에성공() throws Exception {

        // given
        List<Long> hintIdList = saveHints(roomId, 2);
        Gate firstGate = new Gate();
        fakeLlmClient.enqueue(FakeLlmScenario.gated(firstGate, FakeLlmScenario.respond("첫 힌트 보충")));
        String firstRequestId = newRequestId();
        Future<Object> first = submit(() -> followup(hintIdList.get(0), firstRequestId, FOLLOWUP_QUESTION));
        assertThat(firstGate.awaitEntered(WAIT)).isTrue();
        fakeLlmClient.enqueue(FakeLlmScenario.respond("둘째 힌트 보충"));

        // when
        ResultActions sameHint = mockMvc.perform(
                followupRequest(user, roomId, hintIdList.get(0), connectionId, newRequestId(), "다른 보충 질문"));
        ResultActions otherHint = mockMvc.perform(
                followupRequest(user, roomId, hintIdList.get(1), connectionId, newRequestId(), FOLLOWUP_QUESTION));
        firstGate.open();
        Object firstResult = first.get(WAIT.toSeconds(), TimeUnit.SECONDS);

        // then
        expectError(sameHint, ErrorCode.AI_REQUEST_IN_PROGRESS);
        sameHint.andExpect(jsonPath("$.data.detail.requestId").value(firstRequestId));
        otherHint.andExpect(status().isOk()).andExpect(jsonPath("$.data.content").value("둘째 힌트 보충"));
        assertThat(firstResult).isInstanceOf(AiRequestResult.Completed.class);
        assertThat(findFollowupList(hintIdList.get(0))).extracting(HintFollowup::getContent).containsExactly("첫 힌트 보충");
        assertThat(findFollowupList(hintIdList.get(1))).extracting(HintFollowup::getContent).containsExactly("둘째 힌트 보충");
    }

    @Test
    void createHintFollowup_새힌트처리중에도_보충질문가능() throws Exception {

        // given
        Long hintId = saveHints(roomId, 1).getFirst();
        Gate gate = new Gate();
        fakeLlmClient.enqueue(FakeLlmScenario.gated(gate, FakeLlmScenario.respond("새 힌트")));
        Future<Object> newHint = submit(() -> hintService.createHint(authUserOf(user), roomId,
                new HintCreateRequest(newRequestId(), QUESTION), connectionId));
        assertThat(gate.awaitEntered(WAIT)).isTrue();
        fakeLlmClient.enqueue(FakeLlmScenario.respond("보충 설명"));

        // when
        ResultActions result = mockMvc.perform(followupRequest(user, roomId, hintId, connectionId, newRequestId(), FOLLOWUP_QUESTION));
        gate.open();

        // then
        result.andExpect(status().isOk());
        assertThat(newHint.get(WAIT.toSeconds(), TimeUnit.SECONDS)).isInstanceOf(AiRequestResult.Completed.class);
    }

    // --- 차단·실패 ---

    @Test
    void createHintFollowup_차단질문_QUESTION_BLOCKED_미사용_이후보충가능() throws Exception {

        // given
        Long hintId = saveHints(roomId, 1).getFirst();

        // when
        ResultActions blocked = mockMvc.perform(followupRequest(user, roomId, hintId, connectionId, newRequestId(), BLOCKED_QUESTION));
        ResultActions list = mockMvc.perform(hintListRequest(user, roomId));
        ResultActions allowed = mockMvc.perform(followupRequest(user, roomId, hintId, connectionId, newRequestId(), FOLLOWUP_QUESTION));

        // then
        expectError(blocked, ErrorCode.QUESTION_BLOCKED);
        list.andExpect(jsonPath("$.data.hints[0].followupAvailable").value(true));
        allowed.andExpect(status().isOk());
        assertThat(findFollowupList(hintId)).extracting(HintFollowup::getQuestion).containsExactly(FOLLOWUP_QUESTION);
    }

    @Test
    void createHintFollowup_분류실패_502_HINT_FOLLOWUP_FAILED_요청행없음_본모델미호출() throws Exception {

        // given
        Long hintId = saveHints(roomId, 1).getFirst();
        fakeLlmClient.enqueueFilter(FakeLlmScenario.serviceError());
        String requestId = newRequestId();

        // when
        ResultActions failed = mockMvc.perform(followupRequest(user, roomId, hintId, connectionId, requestId, FOLLOWUP_QUESTION));

        // then
        expectError(failed, ErrorCode.HINT_FOLLOWUP_FAILED);
        failed.andExpect(jsonPath("$.data.detail.requestId").value(requestId));
        assertThat(countAiRequests(roomId)).isZero();
        assertThat(fakeLlmClient.callCount(LlmModelTier.MAIN)).isZero();
        assertThat(findUsageLogList(roomId)).extracting(LlmUsageLog::getRequestType, LlmUsageLog::getCallStatus)
                .containsExactly(tuple(LlmRequestType.QUESTION_FILTER, LlmCallStatus.FAILED));
    }

    @Test
    void createHintFollowup_본모델실패_502_미사용_같은요청재시도성공() throws Exception {

        // given
        Long hintId = saveHints(roomId, 1).getFirst();
        fakeLlmClient.enqueue(FakeLlmScenario.timeout(), FakeLlmScenario.respond("재시도 보충 설명"));
        String requestId = newRequestId();

        // when
        ResultActions failed = mockMvc.perform(followupRequest(user, roomId, hintId, connectionId, requestId, FOLLOWUP_QUESTION));
        ResultActions listAfterFailure = mockMvc.perform(hintListRequest(user, roomId));
        ResultActions retried = mockMvc.perform(followupRequest(user, roomId, hintId, connectionId, requestId, FOLLOWUP_QUESTION));

        // then
        expectError(failed, ErrorCode.HINT_FOLLOWUP_FAILED);
        failed.andExpect(jsonPath("$.data.detail.requestId").value(requestId));
        listAfterFailure.andExpect(jsonPath("$.data.hints[0].followup").isEmpty())
                .andExpect(jsonPath("$.data.hints[0].followupAvailable").value(true));
        retried.andExpect(status().isOk()).andExpect(jsonPath("$.data.content").value("재시도 보충 설명"));
        assertThat(findFollowupList(hintId)).hasSize(1);
        assertThat(findAiRequest(roomId, requestId).getExecutionNo()).isEqualTo(2);
        assertThat(findUsageLogList(roomId)).extracting(LlmUsageLog::getRequestType, LlmUsageLog::getCallStatus)
                .containsExactly(
                        tuple(LlmRequestType.QUESTION_FILTER, LlmCallStatus.SUCCEEDED),
                        tuple(LlmRequestType.HINT_FOLLOWUP, LlmCallStatus.FAILED),
                        tuple(LlmRequestType.QUESTION_FILTER, LlmCallStatus.SUCCEEDED),
                        tuple(LlmRequestType.HINT_FOLLOWUP, LlmCallStatus.SUCCEEDED));
    }

    @Test
    void createHintFollowup_빈답변형식오류_502_미사용() throws Exception {

        // given
        Long hintId = saveHints(roomId, 1).getFirst();
        fakeLlmClient.enqueue(FakeLlmScenario.respond(" "));

        // when
        ResultActions failed = mockMvc.perform(followupRequest(user, roomId, hintId, connectionId, newRequestId(), FOLLOWUP_QUESTION));

        // then
        expectError(failed, ErrorCode.HINT_FOLLOWUP_FAILED);
        assertThat(findFollowupList(hintId)).isEmpty();
    }

    @Test
    void createHintFollowup_생성중세션종료_ROOM_ENDED_DURING_REQUEST_미사용() throws Exception {

        // given
        Long hintId = saveHints(roomId, 1).getFirst();
        Gate gate = new Gate();
        fakeLlmClient.enqueue(FakeLlmScenario.gated(gate, FakeLlmScenario.respond("종료 뒤 도착한 설명")));
        Future<Object> execution = submit(() -> followup(hintId, newRequestId(), FOLLOWUP_QUESTION));
        assertThat(gate.awaitEntered(WAIT)).isTrue();
        keepAliveUntil(roomId, T0.plusSeconds(LIMIT_SEC));
        roomLifecycleService.recordHeartbeat(authUserOf(user), roomId, connectionId);

        // when
        gate.open();
        Object lateResult = execution.get(WAIT.toSeconds(), TimeUnit.SECONDS);

        // then
        assertThat(lateResult).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ROOM_ENDED_DURING_REQUEST));
        assertThat(findFollowupList(hintId)).isEmpty();
    }

    // --- 대상·권한·세션 상태 ---

    @Test
    void createHintFollowup_다른세션힌트와없는힌트_HINT_NOT_FOUND_분류호출없음() throws Exception {

        // given: 같은 사용자의 다른 세션 힌트
        Long otherRoomId = saveReadyRoom(user);
        Long otherRoomHintId = saveHints(otherRoomId, 1).getFirst();

        // when
        ResultActions otherRoomHint = mockMvc.perform(
                followupRequest(user, roomId, otherRoomHintId, connectionId, newRequestId(), FOLLOWUP_QUESTION));
        ResultActions missingHint = mockMvc.perform(
                followupRequest(user, roomId, 999_999L, connectionId, newRequestId(), FOLLOWUP_QUESTION));

        // then
        expectError(otherRoomHint, ErrorCode.HINT_NOT_FOUND);
        expectError(missingHint, ErrorCode.HINT_NOT_FOUND);
        assertThat(fakeLlmClient.callCount()).isZero();
        assertThat(countAiRequests(roomId)).isZero();
    }

    @Test
    void createHintFollowup_다른사용자세션_ROOM_NOT_FOUND() throws Exception {

        // given
        Long hintId = saveHints(roomId, 1).getFirst();
        User other = saveUser();

        // when
        ResultActions result = mockMvc.perform(followupRequest(other, roomId, hintId, connectionId, newRequestId(), FOLLOWUP_QUESTION));

        // then
        expectError(result, ErrorCode.ROOM_NOT_FOUND);
        assertThat(fakeLlmClient.callCount()).isZero();
    }

    @Test
    void createHintFollowup_연결헤더없음과일시정지와연결교체_거부_분류호출없음() throws Exception {

        // given
        Long hintId = saveHints(roomId, 1).getFirst();

        // when
        ResultActions noHeader = mockMvc.perform(followupRequest(user, roomId, hintId, null, newRequestId(), FOLLOWUP_QUESTION));
        roomLifecycleService.pauseRoom(authUserOf(user), roomId);
        ResultActions paused = mockMvc.perform(followupRequest(user, roomId, hintId, connectionId, newRequestId(), FOLLOWUP_QUESTION));
        roomLifecycleService.resumeRoom(authUserOf(user), roomId);
        ResultActions replaced = mockMvc.perform(followupRequest(user, roomId, hintId, connectionId, newRequestId(), FOLLOWUP_QUESTION));

        // then
        expectError(noHeader, ErrorCode.CONNECTION_ID_REQUIRED);
        expectError(paused, ErrorCode.ROOM_NOT_IN_PROGRESS);
        expectError(replaced, ErrorCode.CONNECTION_REPLACED);
        assertThat(fakeLlmClient.callCount()).isZero();
    }

    @Test
    void createHintFollowup_시간만료_ROOM_TIME_EXPIRED() throws Exception {

        // given
        Long hintId = saveHints(roomId, 1).getFirst();
        keepAliveUntil(roomId, T0.plusSeconds(LIMIT_SEC));

        // when
        ResultActions result = mockMvc.perform(followupRequest(user, roomId, hintId, connectionId, newRequestId(), FOLLOWUP_QUESTION));

        // then
        expectError(result, ErrorCode.ROOM_TIME_EXPIRED);
        assertThat(fakeLlmClient.callCount()).isZero();
    }

    @Test
    void createHintFollowup_입력오류_INVALID_INPUT() throws Exception {

        // given
        Long hintId = saveHints(roomId, 1).getFirst();

        // when
        ResultActions blank = mockMvc.perform(followupRequest(user, roomId, hintId, connectionId, newRequestId(), "  "));
        ResultActions tooLong = mockMvc.perform(followupRequest(user, roomId, hintId, connectionId, newRequestId(), "가".repeat(501)));

        // then
        blank.andExpect(status().isBadRequest()).andExpect(jsonPath("$.data.code").value(ErrorCode.INVALID_INPUT.name()));
        tooLong.andExpect(status().isBadRequest()).andExpect(jsonPath("$.data.code").value(ErrorCode.INVALID_INPUT.name()));
        assertThat(fakeLlmClient.callCount()).isZero();
    }

    // --- helpers ---

    private AiRequestResult followup(Long hintId, String requestId, String question) {
        return hintService.createHintFollowup(authUserOf(user), roomId, hintId, new HintFollowupRequest(requestId, question), connectionId);
    }
}
