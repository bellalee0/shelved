package com.shelved.domain.accusation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shelved.common.entity.AiRequest;
import com.shelved.common.entity.Room;
import com.shelved.common.enums.AiRequestStatus;
import com.shelved.common.enums.GradingStatus;
import com.shelved.common.enums.RoomStatus;
import com.shelved.common.exception.ErrorCode;
import com.shelved.domain.accusation.AccusationIntegrationTestSupport;
import com.shelved.domain.airequest.service.AiRequestRecoveryService;
import com.shelved.infra.llm.FakeLlmScenario;
import com.shelved.infra.llm.FakeLlmScenario.Gate;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 채점 실패 경로별 지목 행 ERROR, 재채점(최소 간격·마감 후·연결 헤더 없음·원래 접수 시점 유효성), 대체
 */
class AccusationRetryIntegrationTest extends AccusationIntegrationTestSupport {

    @Autowired
    private AiRequestRecoveryService aiRequestRecoveryService;

    // --- 실패 경로별 지목 행 ERROR (기회 미차감) ---

    @Test
    void createAccusation_모델오류_502detail_지목ERROR_기회미차감() throws Exception {

        // given
        fakeLlmClient.enqueue(FakeLlmScenario.serviceError());
        String requestId = newRequestId();

        // when
        ResultActions result = mockMvc.perform(accuseButler(requestId));

        // then
        expectError(result, ErrorCode.GRADING_FAILED);
        Long accusationId = findAccusation(roomId, requestId).getId();
        result.andExpect(jsonPath("$.data.detail.requestId").value(requestId))
                .andExpect(jsonPath("$.data.detail.accusationId").value(accusationId))
                .andExpect(jsonPath("$.data.detail.retryAvailableAt").value("2026-11-12T14:00:10"));
        assertThat(findAccusation(roomId, requestId).getGradingStatus()).isEqualTo(GradingStatus.ERROR);
        mockMvc.perform(stateRequest(user, roomId))
                .andExpect(jsonPath("$.data.accusationCount.used").value(0))
                .andExpect(jsonPath("$.data.accusationCount.reserved").value(0))
                .andExpect(jsonPath("$.data.accusationCount.remaining").value(3))
                .andExpect(jsonPath("$.data.pendingAccusation.accusationId").value(accusationId))
                .andExpect(jsonPath("$.data.pendingAccusation.gradingStatus").value("ERROR"))
                .andExpect(jsonPath("$.data.pendingAccusation.retryAvailableAt").value("2026-11-12T14:00:10"))
                .andExpect(jsonPath("$.data.pendingAccusation.superseded").value(false))
                .andExpect(jsonPath("$.data.aiRequests[0].requestId").value(requestId))
                .andExpect(jsonPath("$.data.aiRequests[0].status").value("FAILED"))
                .andExpect(jsonPath("$.data.aiRequests[0].retryAvailableAt").value("2026-11-12T14:00:10"));
    }

    @Test
    void createAccusation_형식오류_지목ERROR() throws Exception {

        // given: 기대 항목 하나가 빠진 응답
        fakeLlmClient.enqueue(FakeLlmScenario.respond(grading("MET", "MET").replace("trk-1-i2", "trk-9-i9")));
        String requestId = newRequestId();

        // when
        ResultActions result = mockMvc.perform(accuseButler(requestId));

        // then
        expectError(result, ErrorCode.GRADING_FAILED);
        assertThat(findAccusation(roomId, requestId).getGradingStatus()).isEqualTo(GradingStatus.ERROR);
        assertThat(findAccusation(roomId, requestId).getVerdict()).isNull();
    }

    @Test
    void recoverStaleRequests_채점중작업시간초과_지목ERROR_늦은응답미반영() throws Exception {

        // given: 채점 호출이 붙잡힌 동안 작업 시간 제한(60초 + 여유 60초)이 지남
        Gate gate = new Gate();
        fakeLlmClient.enqueue(FakeLlmScenario.gated(gate, FakeLlmScenario.respond(allMetGrading())));
        String requestId = newRequestId();
        Future<Object> pending = submit(() -> bodyOf(mockMvc.perform(accuseButler(requestId))));
        assertThat(gate.awaitEntered(WAIT)).isTrue();
        testClock.advance(Duration.ofSeconds(121));

        // when
        int recovered = aiRequestRecoveryService.recoverStaleRequests();
        gate.open();
        String lateBody = (String) pending.get();

        // then
        // 복구는 모든 세션을 훑으므로 다른 테스트가 남긴 요청도 셀 수 있다
        assertThat(recovered).isPositive();
        assertThat(lateBody).contains("GRADING_FAILED");
        assertThat(findAccusation(roomId, requestId).getGradingStatus()).isEqualTo(GradingStatus.ERROR);
        assertThat(findAiRequest(roomId, requestId).getStatus()).isEqualTo(AiRequestStatus.FAILED);
        assertThat(findPlayResultList(roomId)).isEmpty();
    }

    @Test
    void recoverAllProcessingRequests_서버재시작복구_지목ERROR() throws Exception {

        // given
        Gate gate = new Gate();
        fakeLlmClient.enqueue(FakeLlmScenario.gated(gate, FakeLlmScenario.respond(allMetGrading())));
        String requestId = newRequestId();
        Future<Object> pending = submit(() -> bodyOf(mockMvc.perform(accuseButler(requestId))));
        assertThat(gate.awaitEntered(WAIT)).isTrue();

        // when
        int recovered = aiRequestRecoveryService.recoverAllProcessingRequests();
        gate.open();
        pending.get();

        // then
        // 복구는 모든 세션을 훑으므로 다른 테스트가 남긴 요청도 셀 수 있다
        assertThat(recovered).isPositive();
        assertThat(findAccusation(roomId, requestId).getGradingStatus()).isEqualTo(GradingStatus.ERROR);
        assertThat(findRoom(roomId).getStatus()).isEqualTo(RoomStatus.IN_PROGRESS);
    }

    @Test
    void createAccusation_채점중세션종료_ROOM_ENDED_DURING_REQUEST_지목ERROR() throws Exception {

        // given: 채점 중 세션이 종료됨 (다른 경로의 종료를 DB로 흉내 낸다)
        Gate gate = new Gate();
        fakeLlmClient.enqueue(FakeLlmScenario.gated(gate, FakeLlmScenario.respond(allMetGrading())));
        String requestId = newRequestId();
        Future<Object> pending = submit(() -> bodyOf(mockMvc.perform(accuseButler(requestId))));
        assertThat(gate.awaitEntered(WAIT)).isTrue();
        transactionTemplate.executeWithoutResult(status -> entityManager.createQuery(
                        "update Room r set r.status = :status where r.id = :roomId")
                .setParameter("status", RoomStatus.EXPIRED)
                .setParameter("roomId", roomId)
                .executeUpdate());

        // when
        gate.open();
        String body = (String) pending.get();

        // then
        assertThat(body).contains("ROOM_ENDED_DURING_REQUEST");
        assertThat(findAccusation(roomId, requestId).getGradingStatus()).isEqualTo(GradingStatus.ERROR);
        assertThat(findAiRequest(roomId, requestId).getErrorCode()).isEqualTo("ROOM_ENDED_DURING_REQUEST");
        assertThat(findPlayResultList(roomId)).isEmpty();
    }

    // --- 재채점 ---

    @Test
    void createAccusation_재채점_10초전429_10초후성공_1회만차감() throws Exception {

        // given
        fakeLlmClient.enqueue(FakeLlmScenario.serviceError());
        String requestId = newRequestId();
        mockMvc.perform(accuseButler(requestId)).andExpect(status().isBadGateway());
        Long accusationId = findAccusation(roomId, requestId).getId();
        keepAliveUntil(roomId, T0.plusSeconds(9));

        // when
        ResultActions tooSoon = mockMvc.perform(accuseButler(requestId));
        keepAliveUntil(roomId, T0.plusSeconds(10));
        fakeLlmClient.enqueue(FakeLlmScenario.respond(partialGrading()));
        ResultActions retried = mockMvc.perform(accuseButler(requestId));

        // then
        expectError(tooSoon, ErrorCode.GRADING_RETRY_TOO_SOON);
        tooSoon.andExpect(jsonPath("$.data.detail.retryAvailableAt").value("2026-11-12T14:00:10"));
        retried.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accusation.accusationId").value(accusationId))
                .andExpect(jsonPath("$.data.accusation.submittedAt").value("2026-11-12T14:00:00"))
                .andExpect(jsonPath("$.data.accusation.attemptNo").value(1))
                .andExpect(jsonPath("$.data.accusationCount.used").value(1))
                .andExpect(jsonPath("$.data.accusationCount.remaining").value(2));
        assertThat(findAccusationList(roomId)).singleElement()
                .satisfies(accusation -> assertThat(accusation.getGradingStatus()).isEqualTo(GradingStatus.COMPLETED));
        assertThat(findAiRequest(roomId, requestId).getExecutionNo()).isEqualTo(2);
    }

    @Test
    void createAccusation_재채점_일시정지중_연결헤더없이_세션재개안함_lastSeen불변() throws Exception {

        // given: 실패 후 일시정지
        fakeLlmClient.enqueue(FakeLlmScenario.serviceError());
        String requestId = newRequestId();
        keepAliveUntil(roomId, T0.plusSeconds(30));
        mockMvc.perform(accuseButler(requestId)).andExpect(status().isBadGateway());
        roomLifecycleService.pauseRoom(authUserOf(user), roomId);
        LocalDateTime lastSeenBefore = findRoom(roomId).getLastSeenAt();
        testClock.advance(Duration.ofMinutes(5));
        fakeLlmClient.enqueue(FakeLlmScenario.respond(partialGrading()));

        // when
        ResultActions result = mockMvc.perform(accusationRequest(
                user, roomId, null, requestId, sample.butler().getId(), TRICK));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accusation.verdict").value("PARTIAL"))
                .andExpect(jsonPath("$.data.roomStatus").value("PAUSED"));
        Room room = findRoom(roomId);
        assertThat(room.getStatus()).isEqualTo(RoomStatus.PAUSED);
        assertThat(room.getLastSeenAt()).isEqualTo(lastSeenBefore);
        assertThat(room.getElapsedActiveMs()).isEqualTo(30_000L);
    }

    @Test
    void createAccusation_마감후재채점_원래접수시점유효성으로_CLEARED() throws Exception {

        // given: 제한시간 5초 전 제출 → 채점 실패 → 시간 소진 후 접속 확인으로 채점 대기
        keepAliveUntil(roomId, atElapsed(LIMIT_MS - 5_000L));
        fakeLlmClient.enqueue(FakeLlmScenario.serviceError());
        String requestId = newRequestId();
        mockMvc.perform(accuseButler(requestId)).andExpect(status().isBadGateway());
        keepAliveUntil(roomId, atElapsed(LIMIT_MS + 1_000L));
        roomLifecycleService.recordHeartbeat(authUserOf(user), roomId, connectionId);
        mockMvc.perform(stateRequest(user, roomId))
                .andExpect(jsonPath("$.data.timer.status").value("PAUSED"))
                .andExpect(jsonPath("$.data.timer.awaitingGrading").value(true));
        testClock.advance(Duration.ofSeconds(30));

        // when: 연결 헤더 없이 재채점 (Fake 기본 채점 → 정답)
        ResultActions result = mockMvc.perform(accusationRequest(
                user, roomId, null, requestId, sample.butler().getId(), TRICK));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accusation.verdict").value("CORRECT"))
                .andExpect(jsonPath("$.data.roomStatus").value("CLEARED"));
        assertThat(findPlayResultList(roomId)).singleElement().satisfies(playResult -> {
            assertThat(playResult.isCleared()).isTrue();
            assertThat(playResult.getDurationSec()).isEqualTo(LIMIT_SEC - 5);
        });
    }

    @Test
    void createAccusation_마감후새답안_ROOM_TIME_EXPIRED() throws Exception {

        // given: 실패 지목이 남은 채 시간 소진 → 채점 대기(PAUSED)
        keepAliveUntil(roomId, atElapsed(LIMIT_MS - 5_000L));
        fakeLlmClient.enqueue(FakeLlmScenario.serviceError());
        mockMvc.perform(accuseButler(newRequestId())).andExpect(status().isBadGateway());
        keepAliveUntil(roomId, atElapsed(LIMIT_MS + 1_000L));
        roomLifecycleService.recordHeartbeat(authUserOf(user), roomId, connectionId);

        // when
        ResultActions result = mockMvc.perform(accuseButler(newRequestId()));

        // then
        expectError(result, ErrorCode.ROOM_TIME_EXPIRED);
        assertThat(findAccusationList(roomId)).hasSize(1);
        assertThat(findRoom(roomId).getStatus()).isEqualTo(RoomStatus.PAUSED);
    }

    // --- 대체 ---

    @Test
    void createAccusation_실패후새답안_이전지목대체_재채점거부() throws Exception {

        // given
        fakeLlmClient.enqueue(FakeLlmScenario.serviceError(), FakeLlmScenario.respond(partialGrading()));
        String failedId = newRequestId();
        String newId = newRequestId();
        mockMvc.perform(accuseButler(failedId)).andExpect(status().isBadGateway());
        mockMvc.perform(accuseButler(newId)).andExpect(status().isOk());
        keepAliveUntil(roomId, T0.plusSeconds(30));

        // when
        ResultActions retryOld = mockMvc.perform(accuseButler(failedId));
        ResultActions list = mockMvc.perform(accusationListRequest(user, roomId));
        ResultActions state = mockMvc.perform(stateRequest(user, roomId));

        // then
        expectError(retryOld, ErrorCode.AI_REQUEST_SUPERSEDED);
        list.andExpect(jsonPath("$.data.accusations[0].requestId").value(failedId))
                .andExpect(jsonPath("$.data.accusations[0].gradingStatus").value("ERROR"))
                .andExpect(jsonPath("$.data.accusations[0].superseded").value(true))
                .andExpect(jsonPath("$.data.accusations[1].requestId").value(newId))
                .andExpect(jsonPath("$.data.accusations[1].superseded").value(false))
                .andExpect(jsonPath("$.data.accusations[1].attemptNo").value(1));
        state.andExpect(jsonPath("$.data.pendingAccusation").isEmpty())
                .andExpect(jsonPath("$.data.aiRequests.length()").value(0));
        AiRequest superseded = findAiRequest(roomId, failedId);
        assertThat(superseded.getSupersededAt()).isEqualTo(T0);
    }

    @Test
    void createAccusation_대체된실패지목은_시간만료를막지않음_EXPIRED() throws Exception {

        // given: 실패 → 새 답안 부분 정답(대체) → 시간 소진
        fakeLlmClient.enqueue(FakeLlmScenario.serviceError(), FakeLlmScenario.respond(partialGrading()));
        mockMvc.perform(accuseButler(newRequestId())).andExpect(status().isBadGateway());
        mockMvc.perform(accuseButler(newRequestId())).andExpect(status().isOk());
        keepAliveUntil(roomId, atElapsed(LIMIT_MS + 1_000L));

        // when: 시간 소진 후 접속 확인
        roomLifecycleService.recordHeartbeat(authUserOf(user), roomId, connectionId);

        // then
        assertThat(findRoom(roomId).getStatus()).isEqualTo(RoomStatus.EXPIRED);
        assertThat(findPlayResultList(roomId)).singleElement().satisfies(playResult -> {
            assertThat(playResult.isCleared()).isFalse();
            assertThat(playResult.getDurationSec()).isEqualTo(LIMIT_SEC);
        });
    }

    @Test
    void createAccusation_재채점과새답안동시_하나만접수_상태일관() throws Exception {

        // given: 실패 지목이 있고 10초가 지남
        fakeLlmClient.enqueue(FakeLlmScenario.serviceError());
        String failedId = newRequestId();
        mockMvc.perform(accuseButler(failedId)).andExpect(status().isBadGateway());
        keepAliveUntil(roomId, T0.plusSeconds(11));
        Gate gate = new Gate();
        fakeLlmClient.enqueue(FakeLlmScenario.gated(gate, FakeLlmScenario.respond(partialGrading())));
        String newId = newRequestId();
        MockHttpServletRequestBuilder retry = accuseButler(failedId);
        MockHttpServletRequestBuilder fresh = accuseButler(newId);

        // when
        List<Future<Object>> futureList = submitTogether(
                () -> bodyOf(mockMvc.perform(retry)),
                () -> bodyOf(mockMvc.perform(fresh)));
        String firstDone = (String) awaitFirstDone(futureList);
        gate.open();
        List<Object> bodyList = awaitAll(futureList);

        // then: 먼저 끝난 쪽은 거부, 다른 쪽이 채점 완료
        assertThat(firstDone).containsAnyOf("AI_REQUEST_IN_PROGRESS", "AI_REQUEST_SUPERSEDED");
        assertThat(bodyList).filteredOn(body -> ((String) body).contains("\"success\":true")).hasSize(1);
        assertThat(fakeLlmClient.callCount()).isEqualTo(2);
        mockMvc.perform(stateRequest(user, roomId))
                .andExpect(jsonPath("$.data.accusationCount.used").value(1))
                .andExpect(jsonPath("$.data.accusationCount.reserved").value(0));
        boolean retryWon = findAccusation(roomId, failedId).getGradingStatus() == GradingStatus.COMPLETED;
        if (retryWon) {
            assertThat(findAccusationList(roomId)).hasSize(1);
        } else {
            assertThat(findAiRequest(roomId, failedId).getSupersededAt()).isNotNull();
            assertThat(findAccusation(roomId, newId).getGradingStatus()).isEqualTo(GradingStatus.COMPLETED);
        }
    }
}
