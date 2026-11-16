package com.shelved.domain.hint.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shelved.common.entity.User;
import com.shelved.common.enums.RoomStatus;
import com.shelved.common.exception.ErrorCode;
import com.shelved.domain.airequest.service.AiRequestResult;
import com.shelved.domain.hint.HintIntegrationTestSupport;
import com.shelved.domain.hint.dto.HintCreateRequest;
import com.shelved.domain.hint.dto.HintFollowupRequest;
import com.shelved.domain.hint.service.HintService;
import com.shelved.fixture.ClocktowerCaseFixture;
import com.shelved.infra.llm.FakeLlmScenario;
import com.shelved.infra.llm.FakeLlmScenario.Gate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

/**
 * GET /api/rooms/{roomId}/hints. 시간순 기록·보충 질문·followupAvailable·pendingRequests·hintCount와 종료 후 조회를 확인한다
 */
class HintListIntegrationTest extends HintIntegrationTestSupport {

    @Autowired
    private HintService hintService;

    @Test
    void getHintList_힌트없는세션_빈목록과횟수() throws Exception {

        // when
        ResultActions result = mockMvc.perform(hintListRequest(user, roomId));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("힌트 기록 조회 성공"))
                .andExpect(jsonPath("$.data.hintCount.limit").value(5))
                .andExpect(jsonPath("$.data.hintCount.used").value(0))
                .andExpect(jsonPath("$.data.hintCount.reserved").value(0))
                .andExpect(jsonPath("$.data.hintCount.remaining").value(5))
                .andExpect(jsonPath("$.data.hints").isEmpty())
                .andExpect(jsonPath("$.data.pendingRequests").isEmpty());
        assertThat(readData(result).propertyNames()).containsExactly("hintCount", "hints", "pendingRequests");
    }

    @Test
    void getHintList_오래된순_보충질문포함_followupAvailable_종료후에도조회_정답정보없음() throws Exception {

        // given: 힌트 2개, 첫 힌트만 보충 질문 사용, 이후 시간 만료로 종료
        Long firstHintId = createHintOk("첫 번째 힌트");
        testClock.fixAt(T0.plusSeconds(30));
        keepAliveUntil(roomId, T0.plusSeconds(30));
        Long secondHintId = createHintOk("두 번째 힌트");
        fakeLlmClient.enqueue(FakeLlmScenario.respond("첫 힌트의 보충 설명"));
        mockMvc.perform(followupRequest(user, roomId, firstHintId, connectionId, newRequestId(), FOLLOWUP_QUESTION))
                .andExpect(status().isOk());
        keepAliveUntil(roomId, T0.plusSeconds(LIMIT_SEC));
        roomLifecycleService.recordHeartbeat(authUserOf(user), roomId, connectionId);
        assertThat(findRoom(roomId).getStatus()).isEqualTo(RoomStatus.EXPIRED);

        // when
        ResultActions result = mockMvc.perform(hintListRequest(user, roomId));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hintCount.used").value(2))
                .andExpect(jsonPath("$.data.hintCount.remaining").value(3))
                .andExpect(jsonPath("$.data.hints.length()").value(2))
                .andExpect(jsonPath("$.data.hints[0].hintId").value(firstHintId))
                .andExpect(jsonPath("$.data.hints[0].question").value(QUESTION))
                .andExpect(jsonPath("$.data.hints[0].content").value("첫 번째 힌트"))
                .andExpect(jsonPath("$.data.hints[0].followup.hintId").value(firstHintId))
                .andExpect(jsonPath("$.data.hints[0].followup.question").value(FOLLOWUP_QUESTION))
                .andExpect(jsonPath("$.data.hints[0].followup.content").value("첫 힌트의 보충 설명"))
                .andExpect(jsonPath("$.data.hints[0].followupAvailable").value(false))
                .andExpect(jsonPath("$.data.hints[1].hintId").value(secondHintId))
                .andExpect(jsonPath("$.data.hints[1].followup").isEmpty())
                .andExpect(jsonPath("$.data.hints[1].followupAvailable").value(true))
                .andExpect(jsonPath("$.data.pendingRequests").isEmpty());
        JsonNode firstHint = readData(result).get("hints").get(0);
        assertThat(firstHint.propertyNames())
                .containsExactly("hintId", "requestId", "question", "content", "createdAt", "followup", "followupAvailable");
        assertThat(firstHint.get("followup").propertyNames())
                .containsExactly("hintId", "requestId", "question", "content", "createdAt");
        assertThat(bodyOf(result)).doesNotContain(ClocktowerCaseFixture.TRICK_SUMMARY, "persona", "culprit", "glossary", "input_json");
    }

    @Test
    void getHintList_보충질문처리중_followupAvailable_false_pendingRequests() throws Exception {

        // given
        List<Long> hintIdList = saveHints(roomId, 2);
        Gate gate = new Gate();
        fakeLlmClient.enqueue(FakeLlmScenario.gated(gate, FakeLlmScenario.respond("보충 설명")));
        String requestId = newRequestId();
        Future<Object> processing = submit(() -> hintService.createHintFollowup(authUserOf(user), roomId, hintIdList.get(0),
                new HintFollowupRequest(requestId, FOLLOWUP_QUESTION), connectionId));
        assertThat(gate.awaitEntered(WAIT)).isTrue();

        // when
        ResultActions result = mockMvc.perform(hintListRequest(user, roomId));
        gate.open();

        // then
        result.andExpect(jsonPath("$.data.hints[0].followup").isEmpty())
                .andExpect(jsonPath("$.data.hints[0].followupAvailable").value(false))
                .andExpect(jsonPath("$.data.hints[1].followupAvailable").value(true))
                .andExpect(jsonPath("$.data.hintCount.reserved").value(0))
                .andExpect(jsonPath("$.data.pendingRequests.length()").value(1))
                .andExpect(jsonPath("$.data.pendingRequests[0].requestId").value(requestId))
                .andExpect(jsonPath("$.data.pendingRequests[0].type").value("HINT_FOLLOWUP"))
                .andExpect(jsonPath("$.data.pendingRequests[0].targetId").value(hintIdList.get(0)))
                .andExpect(jsonPath("$.data.pendingRequests[0].status").value("PROCESSING"))
                .andExpect(jsonPath("$.data.pendingRequests[0].input.question").value(FOLLOWUP_QUESTION));
        assertThat(processing.get(WAIT.toSeconds(), TimeUnit.SECONDS)).isInstanceOf(AiRequestResult.Completed.class);
    }

    @Test
    void getHintList_새힌트처리중_reserved1_pendingRequests() throws Exception {

        // given
        Gate gate = new Gate();
        fakeLlmClient.enqueue(FakeLlmScenario.gated(gate, FakeLlmScenario.respond("새 힌트")));
        String requestId = newRequestId();
        Future<Object> processing = submit(() -> hintService.createHint(authUserOf(user), roomId,
                new HintCreateRequest(requestId, QUESTION), connectionId));
        assertThat(gate.awaitEntered(WAIT)).isTrue();

        // when
        ResultActions result = mockMvc.perform(hintListRequest(user, roomId));
        ResultActions state = mockMvc.perform(stateRequest(user, roomId));
        gate.open();

        // then
        result.andExpect(jsonPath("$.data.hints").isEmpty())
                .andExpect(jsonPath("$.data.hintCount.used").value(0))
                .andExpect(jsonPath("$.data.hintCount.reserved").value(1))
                .andExpect(jsonPath("$.data.hintCount.remaining").value(4))
                .andExpect(jsonPath("$.data.pendingRequests[0].type").value("HINT"))
                .andExpect(jsonPath("$.data.pendingRequests[0].targetId").isEmpty())
                .andExpect(jsonPath("$.data.pendingRequests[0].status").value("PROCESSING"));
        state.andExpect(jsonPath("$.data.hintCount.reserved").value(1));
        assertThat(processing.get(WAIT.toSeconds(), TimeUnit.SECONDS)).isInstanceOf(AiRequestResult.Completed.class);
    }

    @Test
    void getHintList_실패한보충질문_followupAvailable_true_재시도대기로pendingRequests() throws Exception {

        // given
        Long hintId = saveHints(roomId, 1).getFirst();
        fakeLlmClient.enqueue(FakeLlmScenario.serviceError());
        String requestId = newRequestId();
        mockMvc.perform(followupRequest(user, roomId, hintId, connectionId, requestId, FOLLOWUP_QUESTION));

        // when
        ResultActions result = mockMvc.perform(hintListRequest(user, roomId));

        // then
        result.andExpect(jsonPath("$.data.hints[0].followupAvailable").value(true))
                .andExpect(jsonPath("$.data.pendingRequests.length()").value(1))
                .andExpect(jsonPath("$.data.pendingRequests[0].requestId").value(requestId))
                .andExpect(jsonPath("$.data.pendingRequests[0].status").value("FAILED"))
                .andExpect(jsonPath("$.data.pendingRequests[0].error.code").value(ErrorCode.HINT_FOLLOWUP_FAILED.name()));
    }

    @Test
    void getHintList_NPC질문요청은pendingRequests에없음() throws Exception {

        // given: NPC 질문 실패(재시도 대기)
        fakeLlmClient.enqueue(FakeLlmScenario.serviceError());
        mockMvc.perform(post("/api/rooms/{roomId}/npc/{suspectId}/ask", roomId, sample.butler().getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .header(CONNECTION_ID_HEADER, connectionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON_MAPPER.writeValueAsString(Map.of("requestId", newRequestId(), "question", QUESTION))))
                .andExpect(status().isBadGateway());

        // when
        ResultActions result = mockMvc.perform(hintListRequest(user, roomId));

        // then
        result.andExpect(jsonPath("$.data.pendingRequests").isEmpty());
    }

    @Test
    void getHintList_다른사용자세션_ROOM_NOT_FOUND() throws Exception {

        // given
        User other = saveUser();

        // when
        ResultActions result = mockMvc.perform(hintListRequest(other, roomId));

        // then
        expectError(result, ErrorCode.ROOM_NOT_FOUND);
    }
}
