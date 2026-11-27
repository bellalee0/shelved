package com.shelved.domain.room.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shelved.common.entity.MysteryCase;
import com.shelved.common.entity.User;
import com.shelved.common.enums.RoomStatus;
import com.shelved.common.exception.ErrorCode;
import com.shelved.domain.accusation.AccusationIntegrationTestSupport;
import com.shelved.fixture.ClocktowerCaseFixture;
import com.shelved.infra.llm.FakeLlmScenario;
import com.shelved.infra.llm.FakeLlmScenario.Gate;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;

/**
 * GET /api/rooms/{roomId}/result. 종료 상태는 지목·시간 정산의 실제 경로(Fake 채점)로 만든다
 */
class RoomResultIntegrationTest extends AccusationIntegrationTestSupport {

    private static final String[] REVEAL_KEYS = {
            "\"source\"", "\"culprit\"", "\"trickSummary\"", "\"revealExplanation\"",
            ClocktowerCaseFixture.SOURCE_NOVEL_TITLE, ClocktowerCaseFixture.TRICK_SUMMARY
    };

    // --- 종료 상태별 결과 ---

    @Test
    void getRoomResult_CLEARED_접수시점기록과리빌_지목기록순서() throws Exception {

        // given: 힌트 2회, 부분 정답 → 채점 실패 → 정답(채점이 접수 40초 뒤에 끝남)
        saveHints(roomId, 2);
        fakeLlmClient.enqueue(FakeLlmScenario.respond(partialGrading()));
        keepAliveUntil(roomId, atElapsed(100_000L));
        mockMvc.perform(accuseButler(newRequestId())).andExpect(status().isOk());
        fakeLlmClient.enqueue(FakeLlmScenario.serviceError());
        keepAliveUntil(roomId, atElapsed(200_000L));
        mockMvc.perform(accuseButler(newRequestId())).andExpect(status().isBadGateway());
        Gate gate = new Gate();
        fakeLlmClient.enqueue(FakeLlmScenario.gated(gate, FakeLlmScenario.respond(allMetGrading())));
        keepAliveUntil(roomId, atElapsed(300_900L));
        Future<Object> pending = submit(() -> bodyOf(mockMvc.perform(accuseButler(newRequestId()))));
        assertThat(gate.awaitEntered(WAIT)).isTrue();
        keepAliveUntil(roomId, atElapsed(340_000L));
        gate.open();
        pending.get();
        JsonNode recorded = readData(mockMvc.perform(accusationListRequest(user, roomId))).get("accusations");

        // when
        ResultActions result = mockMvc.perform(resultRequest(user, roomId));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("세션 결과 조회 성공"))
                .andExpect(jsonPath("$.data.roomId").value(roomId))
                .andExpect(jsonPath("$.data.caseId").value(sample.mysteryCase().getId()))
                .andExpect(jsonPath("$.data.displayTitle").value(ClocktowerCaseFixture.DISPLAY_TITLE))
                .andExpect(jsonPath("$.data.isTest").value(false))
                .andExpect(jsonPath("$.data.status").value("CLEARED"))
                .andExpect(jsonPath("$.data.endedAt").value("2026-11-12T14:05:40"))
                .andExpect(jsonPath("$.data.durationSec").value(300))
                .andExpect(jsonPath("$.data.hintUsedCount").value(2))
                .andExpect(jsonPath("$.data.accusationCompletedCount").value(2))
                .andExpect(jsonPath("$.data.source.novelTitle").value(ClocktowerCaseFixture.SOURCE_NOVEL_TITLE))
                .andExpect(jsonPath("$.data.source.novelAuthor").value(ClocktowerCaseFixture.SOURCE_NOVEL_AUTHOR))
                .andExpect(jsonPath("$.data.source.novelIntro").value(ClocktowerCaseFixture.SOURCE_NOVEL_INTRO))
                .andExpect(jsonPath("$.data.culprit.suspectId").value(sample.butler().getId()))
                .andExpect(jsonPath("$.data.culprit.name").value(sample.butler().getName()))
                .andExpect(jsonPath("$.data.trickSummary").value(ClocktowerCaseFixture.TRICK_SUMMARY))
                .andExpect(jsonPath("$.data.revealExplanation").value(ClocktowerCaseFixture.REVEAL_EXPLANATION))
                .andExpect(jsonPath("$.data.accusations.length()").value(3))
                .andExpect(jsonPath("$.data.accusations[0].verdict").value("PARTIAL"))
                .andExpect(jsonPath("$.data.accusations[1].gradingStatus").value("ERROR"))
                .andExpect(jsonPath("$.data.accusations[1].superseded").value(true))
                .andExpect(jsonPath("$.data.accusations[2].verdict").value("CORRECT"))
                .andExpect(jsonPath("$.data.accusations[2].attemptNo").value(2));
        assertThat(readData(result).get("accusations")).isEqualTo(recorded);
        // 채점 기준·항목별 판정·인물 설정은 종료 후에도 반환하지 않는다
        assertThat(bodyOf(result)).doesNotContain("elementResults", "culpritCorrect", "gradingRubric", "personaPrompt", "glossary");
    }

    @Test
    void getRoomResult_FAILED_종료시점기록() throws Exception {

        // given
        saveCompletedAccusations(roomId, 2);
        fakeLlmClient.enqueue(FakeLlmScenario.respond(grading("MISSING", "MISSING")));
        keepAliveUntil(roomId, atElapsed(200_000L));
        mockMvc.perform(accuseButler(newRequestId())).andExpect(status().isOk());

        // when
        ResultActions result = mockMvc.perform(resultRequest(user, roomId));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.durationSec").value(200))
                .andExpect(jsonPath("$.data.hintUsedCount").value(0))
                .andExpect(jsonPath("$.data.accusationCompletedCount").value(3))
                .andExpect(jsonPath("$.data.accusations.length()").value(3))
                .andExpect(jsonPath("$.data.culprit.suspectId").value(sample.butler().getId()));
    }

    @Test
    void getRoomResult_EXPIRED_제한시간기록_재조회가능() throws Exception {

        // given
        keepAliveUntil(roomId, atElapsed(LIMIT_MS));
        roomLifecycleService.recordHeartbeat(authUserOf(user), roomId, connectionId);

        // when
        ResultActions first = mockMvc.perform(resultRequest(user, roomId));
        testClock.fixAt(atElapsed(LIMIT_MS + 3_600_000L));
        ResultActions second = mockMvc.perform(resultRequest(user, roomId));

        // then
        first.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("EXPIRED"))
                .andExpect(jsonPath("$.data.durationSec").value(LIMIT_SEC))
                .andExpect(jsonPath("$.data.endedAt").value("2026-11-12T14:20:00"))
                .andExpect(jsonPath("$.data.accusationCompletedCount").value(0))
                .andExpect(jsonPath("$.data.accusations").isEmpty())
                .andExpect(jsonPath("$.data.revealExplanation").value(ClocktowerCaseFixture.REVEAL_EXPLANATION));
        assertThat(readData(second)).isEqualTo(readData(first));
    }

    // --- 종료 전 정답 보호 ---

    @Test
    void getRoomResult_시작전_ROOM_NOT_ENDED() throws Exception {

        // given
        User owner = saveUser();
        Long readyRoomId = saveReadyRoom(owner);

        // when
        ResultActions result = mockMvc.perform(resultRequest(owner, readyRoomId));

        // then
        expectNotEnded(result);
    }

    @Test
    void getRoomResult_진행중과일시정지_ROOM_NOT_ENDED() throws Exception {

        // given: 이 세션은 IN_PROGRESS

        // when
        ResultActions inProgress = mockMvc.perform(resultRequest(user, roomId));
        roomLifecycleService.pauseRoom(authUserOf(user), roomId);
        ResultActions paused = mockMvc.perform(resultRequest(user, roomId));

        // then
        expectNotEnded(inProgress);
        expectNotEnded(paused);
    }

    @Test
    void getRoomResult_채점대기_ROOM_NOT_ENDED() throws Exception {

        // given: 마감 전 접수 지목의 채점 실패 후 시간 소진
        fakeLlmClient.enqueue(FakeLlmScenario.serviceError());
        keepAliveUntil(roomId, atElapsed(LIMIT_MS - 5_000L));
        mockMvc.perform(accuseButler(newRequestId())).andExpect(status().isBadGateway());
        keepAliveUntil(roomId, atElapsed(LIMIT_MS + 1_000L));
        roomLifecycleService.recordHeartbeat(authUserOf(user), roomId, connectionId);
        mockMvc.perform(stateRequest(user, roomId))
                .andExpect(jsonPath("$.data.timer.awaitingGrading").value(true));

        // when
        ResultActions result = mockMvc.perform(resultRequest(user, roomId));

        // then
        expectNotEnded(result);
        assertThat(findPlayResultList(roomId)).isEmpty();
    }

    // --- 조회 시 정산 (GET state와 같은 규칙) ---

    @Test
    void getRoomResult_이탈후시간소진_정산해만료결과반환() throws Exception {

        // given: 마지막 접속 확인 시점에 이미 시간 소진, 이후 이탈
        keepAliveUntil(roomId, atElapsed(LIMIT_MS));
        testClock.fixAt(atElapsed(LIMIT_MS + 60_000L));

        // when
        ResultActions result = mockMvc.perform(resultRequest(user, roomId));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("EXPIRED"))
                .andExpect(jsonPath("$.data.durationSec").value(LIMIT_SEC));
        assertThat(findPlayResultList(roomId)).hasSize(1);
    }

    @Test
    void getRoomResult_이탈했지만시간남음_ROOM_NOT_ENDED_일시정지정산유지() throws Exception {

        // given
        keepAliveUntil(roomId, atElapsed(100_000L));
        testClock.fixAt(atElapsed(200_000L));

        // when
        ResultActions result = mockMvc.perform(resultRequest(user, roomId));

        // then
        expectNotEnded(result);
        assertThat(findRoom(roomId).getStatus()).isEqualTo(RoomStatus.PAUSED);
        assertThat(findRoom(roomId).getElapsedActiveMs()).isEqualTo(100_000L);
    }

    // --- 접근 ---

    @Test
    void getRoomResult_다른사용자_ROOM_NOT_FOUND_비로그인_401() throws Exception {

        // given
        mockMvc.perform(accuseButler(newRequestId())).andExpect(status().isOk());

        // when
        ResultActions other = mockMvc.perform(resultRequest(saveUser(), roomId));
        ResultActions anonymous = mockMvc.perform(get("/api/rooms/{roomId}/result", roomId));

        // then
        expectError(other, ErrorCode.ROOM_NOT_FOUND);
        assertThat(bodyOf(other)).doesNotContain(REVEAL_KEYS);
        expectError(anonymous, ErrorCode.LOGIN_REQUIRED);
    }

    // --- 데이터 오류 ---

    static Stream<Arguments> brokenData() {
        Consumer<RoomResultIntegrationTest> noPlayResult = test -> test.entityManager
                .createQuery("delete from PlayResult p where p.room.id = :roomId")
                .setParameter("roomId", test.roomId)
                .executeUpdate();
        Consumer<RoomResultIntegrationTest> noSource = test -> test.managedCase()
                .updateSourceNovel(ClocktowerCaseFixture.SOURCE_NOVEL_TITLE, " ", ClocktowerCaseFixture.SOURCE_NOVEL_INTRO);
        Consumer<RoomResultIntegrationTest> noCulprit = test -> test.managedCase()
                .updateSolution(null, ClocktowerCaseFixture.TRICK_SUMMARY, ClocktowerCaseFixture.REVEAL_EXPLANATION);
        Consumer<RoomResultIntegrationTest> noReveal = test -> {
            MysteryCase mysteryCase = test.managedCase();
            mysteryCase.updateSolution(mysteryCase.getCulpritSuspect(), null, null);
        };

        return Stream.of(
                Arguments.of("PLAY_RESULTS_없음", noPlayResult),
                Arguments.of("원작정보_공백", noSource),
                Arguments.of("범인_없음", noCulprit),
                Arguments.of("트릭과해설_없음", noReveal)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("brokenData")
    void getRoomResult_데이터누락_500(String name, Consumer<RoomResultIntegrationTest> breakData) throws Exception {

        // given
        mockMvc.perform(accuseButler(newRequestId())).andExpect(status().isOk());
        transactionTemplate.executeWithoutResult(transaction -> breakData.accept(this));

        // when
        ResultActions result = mockMvc.perform(resultRequest(user, roomId));

        // then
        expectError(result, ErrorCode.INTERNAL_SERVER_ERROR);
        assertThat(bodyOf(result)).doesNotContain(REVEAL_KEYS);
    }

    private MysteryCase managedCase() {
        return entityManager.find(MysteryCase.class, sample.mysteryCase().getId());
    }

    private MockHttpServletRequestBuilder resultRequest(User owner, Long targetRoomId) {
        return get("/api/rooms/{roomId}/result", targetRoomId)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner));
    }

    private static void expectNotEnded(ResultActions result) throws Exception {
        expectError(result, ErrorCode.ROOM_NOT_ENDED);
        result.andExpect(jsonPath("$.data.source").doesNotExist())
                .andExpect(jsonPath("$.data.culprit").doesNotExist())
                .andExpect(jsonPath("$.data.trickSummary").doesNotExist())
                .andExpect(jsonPath("$.data.revealExplanation").doesNotExist());
        assertThat(bodyOf(result)).doesNotContain(REVEAL_KEYS);
    }
}
