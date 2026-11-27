package com.shelved.domain.accusation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shelved.common.enums.GradingStatus;
import com.shelved.common.enums.RoomStatus;
import com.shelved.common.exception.ErrorCode;
import com.shelved.domain.accusation.AccusationIntegrationTestSupport;
import com.shelved.domain.room.service.RoomSettlementService;
import com.shelved.infra.llm.FakeLlmScenario;
import com.shelved.infra.llm.FakeLlmScenario.Gate;
import java.util.List;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 결과 반영(CLEARED·FAILED·EXPIRED)과 시간 경계, 동시 요청, PLAY_RESULTS 1건
 */
class AccusationResultIntegrationTest extends AccusationIntegrationTestSupport {

    @Autowired
    private RoomSettlementService roomSettlementService;

    // --- 시간 경계 ---

    @Test
    void createAccusation_제한시간직전접수_성공_직후접수_ROOM_TIME_EXPIRED() throws Exception {

        // given
        fakeLlmClient.enqueue(FakeLlmScenario.respond(partialGrading()));
        keepAliveUntil(roomId, atElapsed(LIMIT_MS - 1L));

        // when
        ResultActions justBefore = mockMvc.perform(accuseButler(newRequestId()));
        keepAliveUntil(roomId, atElapsed(LIMIT_MS));
        ResultActions justAfter = mockMvc.perform(accuseButler(newRequestId()));

        // then
        justBefore.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accusation.verdict").value("PARTIAL"));
        expectError(justAfter, ErrorCode.ROOM_TIME_EXPIRED);
        assertThat(findAccusationList(roomId)).singleElement()
                .satisfies(accusation -> assertThat(accusation.getSubmittedActiveMs()).isEqualTo(LIMIT_MS - 1L));
        assertThat(findRoom(roomId).getStatus()).isEqualTo(RoomStatus.EXPIRED);
        assertThat(findPlayResultList(roomId)).hasSize(1);
    }

    static Stream<Arguments> gradingDuringExpiry() {
        return Stream.of(
                Arguments.of("부분정답_EXPIRED", 0, "PARTIAL", RoomStatus.EXPIRED, false, LIMIT_SEC),
                Arguments.of("정답_CLEARED_접수시점기록", 0, "CORRECT", RoomStatus.CLEARED, true, LIMIT_SEC - 5),
                Arguments.of("3번째오답_FAILED", 2, "INCORRECT", RoomStatus.FAILED, false, LIMIT_SEC)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("gradingDuringExpiry")
    void createAccusation_채점중시간만료_채점대기후결과반영(
            String name,
            int completedBefore,
            String verdict,
            RoomStatus expectedStatus,
            boolean cleared,
            int expectedDurationSec
    ) throws Exception {

        // given: 제한시간 5초 전 접수, 채점이 붙잡힌 동안 시간 소진 → 접속 확인 → 채점 대기
        saveCompletedAccusations(roomId, completedBefore);
        keepAliveUntil(roomId, atElapsed(LIMIT_MS - 5_000L));
        Gate gate = new Gate();
        String response = switch (verdict) {
            case "CORRECT" -> allMetGrading();
            case "PARTIAL" -> partialGrading();
            default -> grading("MISSING", "MISSING");
        };
        fakeLlmClient.enqueue(FakeLlmScenario.gated(gate, FakeLlmScenario.respond(response)));
        Future<Object> pending = submit(() -> bodyOf(mockMvc.perform(accuseButler(newRequestId()))));
        assertThat(gate.awaitEntered(WAIT)).isTrue();
        keepAliveUntil(roomId, atElapsed(LIMIT_MS + 2_000L));
        roomLifecycleService.recordHeartbeat(authUserOf(user), roomId, connectionId);
        mockMvc.perform(stateRequest(user, roomId))
                .andExpect(jsonPath("$.data.timer.status").value("PAUSED"))
                .andExpect(jsonPath("$.data.timer.remainingMs").value(0))
                .andExpect(jsonPath("$.data.timer.awaitingGrading").value(true))
                .andExpect(jsonPath("$.data.accusationCount.reserved").value(1))
                .andExpect(jsonPath("$.data.pendingAccusation.gradingStatus").value("PENDING"));
        assertThat(findPlayResultList(roomId)).isEmpty();

        // when
        gate.open();
        String body = (String) pending.get();

        // then
        assertThat(body).contains("\"verdict\":\"" + verdict + "\"", "\"roomStatus\":\"" + expectedStatus + "\"");
        assertThat(findRoom(roomId).getStatus()).isEqualTo(expectedStatus);
        assertThat(findPlayResultList(roomId)).singleElement().satisfies(playResult -> {
            assertThat(playResult.isCleared()).isEqualTo(cleared);
            assertThat(playResult.getDurationSec()).isEqualTo(expectedDurationSec);
        });
        mockMvc.perform(stateRequest(user, roomId))
                .andExpect(jsonPath("$.data.timer.awaitingGrading").value(false));
    }

    // --- 동시 요청 ---

    @Test
    void createAccusation_같은세션동시새지목_하나만접수_AI_REQUEST_IN_PROGRESS() throws Exception {

        // given
        Gate gate = new Gate();
        fakeLlmClient.enqueue(FakeLlmScenario.gated(gate, FakeLlmScenario.respond(partialGrading())));
        MockHttpServletRequestBuilder first = accuseButler(newRequestId());
        MockHttpServletRequestBuilder second = accuseButler(newRequestId());

        // when
        List<Future<Object>> futureList = submitTogether(
                () -> bodyOf(mockMvc.perform(first)),
                () -> bodyOf(mockMvc.perform(second)));
        String firstDone = (String) awaitFirstDone(futureList);
        gate.open();
        awaitAll(futureList);

        // then
        assertThat(firstDone).contains("AI_REQUEST_IN_PROGRESS");
        assertThat(findAccusationList(roomId)).singleElement()
                .satisfies(accusation -> assertThat(accusation.getGradingStatus()).isEqualTo(GradingStatus.COMPLETED));
        assertThat(countAiRequests(roomId)).isEqualTo(1);
        assertThat(fakeLlmClient.callCount()).isEqualTo(1);
    }

    @Test
    void createAccusation_같은requestId동시_지목행1개_처리중202() throws Exception {

        // given
        Gate gate = new Gate();
        fakeLlmClient.enqueue(FakeLlmScenario.gated(gate, FakeLlmScenario.respond(partialGrading())));
        String requestId = newRequestId();
        MockHttpServletRequestBuilder request = accuseButler(requestId);

        // when
        List<Future<Object>> futureList = submitTogether(
                () -> mockMvc.perform(request).andReturn().getResponse().getStatus(),
                () -> mockMvc.perform(request).andReturn().getResponse().getStatus());
        Object firstDone = awaitFirstDone(futureList);
        gate.open();
        List<Object> statusList = awaitAll(futureList);

        // then
        assertThat(firstDone).isEqualTo(202);
        assertThat(statusList).containsExactlyInAnyOrder(200, 202);
        assertThat(findAccusationList(roomId)).hasSize(1);
        assertThat(fakeLlmClient.callCount()).isEqualTo(1);
    }

    // --- 결과 1건 ---

    @Test
    void createAccusation_정답도착과만료점검이겹쳐도_결과1건_CLEARED() throws Exception {

        // given: 마감 직전 접수, 채점 중 이탈 기준까지 지남
        keepAliveUntil(roomId, atElapsed(LIMIT_MS - 3_000L));
        Gate gate = new Gate();
        fakeLlmClient.enqueue(FakeLlmScenario.gated(gate, FakeLlmScenario.respond(allMetGrading())));
        Future<Object> pending = submit(() -> bodyOf(mockMvc.perform(accuseButler(newRequestId()))));
        assertThat(gate.awaitEntered(WAIT)).isTrue();
        testClock.fixAt(atElapsed(LIMIT_MS + 20_000L));

        // when: 정답 확정과 주기 점검·접속 확인을 동시에
        List<Future<Object>> futureList = submitTogether(
                () -> {
                    gate.open();
                    return pending.get();
                },
                () -> {
                    roomSettlementService.settleRoom(roomId);
                    return "settled";
                },
                () -> {
                    roomLifecycleService.pauseDisconnectedRooms();
                    return "checked";
                });
        awaitAll(futureList);

        // then
        assertThat(findRoom(roomId).getStatus()).isEqualTo(RoomStatus.CLEARED);
        assertThat(findPlayResultList(roomId)).singleElement().satisfies(playResult -> {
            assertThat(playResult.isCleared()).isTrue();
            assertThat(playResult.getDurationSec()).isEqualTo(LIMIT_SEC - 3);
        });
    }

    @Test
    void createAccusation_정답종료후_처리중힌트의늦은응답_ROOM_ENDED_DURING_REQUEST() throws Exception {

        // given: 힌트 답변이 붙잡힌 동안 정답 지목 (Fake 기본 채점)
        Gate gate = new Gate();
        fakeLlmClient.enqueue(FakeLlmScenario.gated(gate, FakeLlmScenario.respond("서재의 시계를 다시 보세요.")));
        Future<Object> hint = submit(() -> bodyOf(mockMvc.perform(hintRequest(user, roomId, connectionId, newRequestId(), QUESTION))));
        assertThat(gate.awaitEntered(WAIT)).isTrue();
        mockMvc.perform(accuseButler(newRequestId()))
                .andExpect(jsonPath("$.data.roomStatus").value("CLEARED"));

        // when
        gate.open();
        String hintBody = (String) hint.get();

        // then
        assertThat(hintBody).contains("ROOM_ENDED_DURING_REQUEST");
        assertThat(findHintList(roomId)).isEmpty();
        assertThat(findPlayResultList(roomId)).hasSize(1);
    }
}
