package com.shelved.domain.ranking.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shelved.common.entity.Accusation;
import com.shelved.common.entity.MysteryCase;
import com.shelved.common.entity.PlayResult;
import com.shelved.common.entity.Room;
import com.shelved.common.entity.RoomParticipant;
import com.shelved.common.entity.Suspect;
import com.shelved.common.entity.User;
import com.shelved.common.enums.UserRole;
import com.shelved.common.enums.Verdict;
import com.shelved.common.exception.ErrorCode;
import com.shelved.domain.accusation.AccusationIntegrationTestSupport;
import com.shelved.fixture.ClocktowerCaseFixture;
import com.shelved.infra.llm.FakeLlmScenario;
import com.shelved.infra.llm.FakeLlmScenario.Gate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;

/**
 * GET /api/rankings/{caseId}. 성공 기록은 지목 API의 실제 경로(Fake 채점)로 만들고,
 * 정답 지목 수가 어긋난 데이터 오류만 직접 저장한다. 사건을 테스트마다 새로 만들어 집계가 섞이지 않는다
 */
class RankingIntegrationTest extends AccusationIntegrationTestSupport {

    // 달성 순서를 만들기 위한 세션 시작 시각 (서로 다른 날)
    private static final LocalDateTime DAY1 = T0.plusDays(1);
    private static final LocalDateTime DAY2 = T0.plusDays(2);
    private static final LocalDateTime DAY3 = T0.plusDays(3);

    // --- 집계 대상 ---

    @Test
    void getRanking_기록없음_빈목록과myRanking_null() throws Exception {

        // given: 진행 중 세션만 있음

        // when
        ResultActions result = mockMvc.perform(rankingRequest(user));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("랭킹 조회 성공"))
                .andExpect(jsonPath("$.data.caseId").value(sample.mysteryCase().getId()))
                .andExpect(jsonPath("$.data.displayTitle").value(ClocktowerCaseFixture.DISPLAY_TITLE))
                .andExpect(jsonPath("$.data.rankingPage.content").isEmpty())
                .andExpect(jsonPath("$.data.rankingPage.totalElements").value(0))
                .andExpect(jsonPath("$.data.rankingPage.size").value(20))
                .andExpect(jsonPath("$.data.rankingPage.number").value(0));
        assertThat(readData(result).has("myRanking")).isTrue();
        assertThat(readData(result).get("myRanking").isNull()).isTrue();
    }

    @Test
    void getRanking_실패_만료_테스트세션_제외() throws Exception {

        // given
        User cleared = saveUser();
        clearRoom(cleared, DAY1, 500_000L);

        User failed = saveUser();
        Long failedRoomId = startRoomAt(failed, DAY1, false);
        saveCompletedAccusations(failedRoomId, 2);
        fakeLlmClient.enqueue(FakeLlmScenario.respond(grading("MISSING", "MISSING")));
        accuse(failed, failedRoomId, DAY1, 100_000L);

        User expired = saveUser();
        Long expiredRoomId = startRoomAt(expired, DAY1, false);
        keepAliveUntil(expiredRoomId, DAY1.plusSeconds(LIMIT_SEC));
        mockMvc.perform(stateRequest(expired, expiredRoomId));
        testClock.fixAt(DAY1.plusSeconds(LIMIT_SEC + 60));
        mockMvc.perform(stateRequest(expired, expiredRoomId))
                .andExpect(jsonPath("$.data.timer.status").value("EXPIRED"));

        User admin = saveAdmin();
        Long testRoomId = startRoomAt(admin, DAY1, true);
        accuse(admin, testRoomId, DAY1, 50_000L);
        assertThat(findPlayResultList(testRoomId)).singleElement()
                .satisfies(playResult -> assertThat(playResult.isCleared()).isTrue());

        // when
        ResultActions result = mockMvc.perform(rankingRequest(admin));

        // then
        result.andExpect(jsonPath("$.data.rankingPage.totalElements").value(1))
                .andExpect(jsonPath("$.data.rankingPage.content[0].rank").value(1))
                .andExpect(jsonPath("$.data.rankingPage.content[0].nickname").value(cleared.getNickname()))
                .andExpect(jsonPath("$.data.rankingPage.content[0].durationSec").value(500));
        assertThat(readData(result).get("myRanking").isNull()).isTrue();
    }

    @Test
    void getRanking_재도전_최고기록1건_같은초면먼저달성한기록() throws Exception {

        // given: 900초 → 700초(DAY2) → 같은 700초(DAY3, 밀리초는 더 빠름) → 800초
        clearRoom(user, DAY1, 900_000L);
        User player = saveUser();
        clearRoom(player, DAY1, 900_000L);
        clearRoom(player, DAY2, 700_900L);
        clearRoom(player, DAY3, 700_100L);
        clearRoom(player, DAY3.plusDays(1), 800_000L);

        // when
        ResultActions result = mockMvc.perform(rankingRequest(player));

        // then
        result.andExpect(jsonPath("$.data.rankingPage.totalElements").value(2))
                .andExpect(jsonPath("$.data.myRanking.rank").value(1))
                .andExpect(jsonPath("$.data.myRanking.durationSec").value(700))
                .andExpect(jsonPath("$.data.myRanking.clearedAt").value("2026-11-14T14:11:40.9"))
                .andExpect(jsonPath("$.data.rankingPage.content[0].clearedAt").value("2026-11-14T14:11:40.9"));
    }

    @Test
    void getRanking_힌트횟수는순위에반영하지않음_응답에사용자ID없음() throws Exception {

        // given: 빠른 쪽이 힌트를 많이 씀
        User fast = saveUser();
        Long fastRoomId = startRoomAt(fast, DAY1, false);
        saveHints(fastRoomId, 5);
        accuse(fast, fastRoomId, DAY1, 100_000L);
        User slow = saveUser();
        clearRoom(slow, DAY1, 101_000L);

        // when
        ResultActions result = mockMvc.perform(rankingRequest(slow));

        // then
        JsonNode content = readData(result).get("rankingPage").get("content");
        assertThat(nicknames(content)).containsExactly(fast.getNickname(), slow.getNickname());
        assertThat(content.get(0).propertyNames())
                .containsExactlyInAnyOrder("rank", "nickname", "durationSec", "clearedAt");
        assertThat(readData(result).get("myRanking").propertyNames())
                .containsExactlyInAnyOrder("rank", "nickname", "durationSec", "clearedAt");
    }

    // --- 공동 순위·페이지 ---

    @Test
    void getRanking_공동순위1224_같은순위는달성순_페이지경계유지_전체기준myRanking() throws Exception {

        // given: 같은 912초 중 나중에 달성한 사용자를 먼저 만든다 (ID 순서와 달성 순서를 다르게)
        User fourth = saveUser();
        User secondLater = saveUser();
        User secondEarlier = saveUser();
        User first = saveUser();
        clearRoom(fourth, DAY1, 1_001_000L);
        clearRoom(secondLater, DAY3, 912_000L);
        clearRoom(secondEarlier, DAY2, 912_500L);
        clearRoom(first, DAY3, 840_000L);

        // when
        ResultActions all = mockMvc.perform(rankingRequest(fourth));
        ResultActions page0 = mockMvc.perform(rankingRequest(fourth).param("size", "2"));
        ResultActions page1 = mockMvc.perform(rankingRequest(first).param("size", "2").param("page", "1"));
        ResultActions beyond = mockMvc.perform(rankingRequest(secondLater).param("size", "2").param("page", "5"));

        // then
        JsonNode content = readData(all).get("rankingPage").get("content");
        assertThat(nicknames(content)).containsExactly(first.getNickname(), secondEarlier.getNickname(),
                secondLater.getNickname(), fourth.getNickname());
        assertThat(ranks(content)).containsExactly(1, 2, 2, 4);

        assertThat(ranks(readData(page0).get("rankingPage").get("content"))).containsExactly(1, 2);
        assertThat(nicknames(readData(page0).get("rankingPage").get("content")))
                .containsExactly(first.getNickname(), secondEarlier.getNickname());
        page0.andExpect(jsonPath("$.data.rankingPage.totalElements").value(4))
                .andExpect(jsonPath("$.data.rankingPage.totalPages").value(2))
                .andExpect(jsonPath("$.data.myRanking.rank").value(4))
                .andExpect(jsonPath("$.data.myRanking.nickname").value(fourth.getNickname()));

        assertThat(ranks(readData(page1).get("rankingPage").get("content"))).containsExactly(2, 4);
        assertThat(nicknames(readData(page1).get("rankingPage").get("content")))
                .containsExactly(secondLater.getNickname(), fourth.getNickname());
        page1.andExpect(jsonPath("$.data.rankingPage.number").value(1))
                .andExpect(jsonPath("$.data.rankingPage.totalElements").value(4))
                .andExpect(jsonPath("$.data.myRanking.rank").value(1));

        beyond.andExpect(jsonPath("$.data.rankingPage.content").isEmpty())
                .andExpect(jsonPath("$.data.rankingPage.totalElements").value(4))
                .andExpect(jsonPath("$.data.myRanking.rank").value(2));
    }

    @Test
    void getRanking_시간과달성시각이모두같으면_사용자ID순() throws Exception {

        // given
        User smallerId = saveUser();
        User largerId = saveUser();
        clearRoom(largerId, DAY1, 600_000L);
        clearRoom(smallerId, DAY1, 600_000L);

        // when
        ResultActions page0 = mockMvc.perform(rankingRequest(user).param("size", "1"));
        ResultActions page1 = mockMvc.perform(rankingRequest(user).param("size", "1").param("page", "1"));

        // then
        page0.andExpect(jsonPath("$.data.rankingPage.content[0].nickname").value(smallerId.getNickname()))
                .andExpect(jsonPath("$.data.rankingPage.content[0].rank").value(1));
        page1.andExpect(jsonPath("$.data.rankingPage.content[0].nickname").value(largerId.getNickname()))
                .andExpect(jsonPath("$.data.rankingPage.content[0].rank").value(1));
    }

    @Test
    void getRanking_size상한100_sort무시() throws Exception {

        // given
        clearRoom(saveUser(), DAY1, 600_000L);

        // when
        ResultActions result = mockMvc.perform(rankingRequest(user)
                .param("size", "1000")
                .param("sort", "nickname,desc"));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rankingPage.size").value(100))
                .andExpect(jsonPath("$.data.rankingPage.content.length()").value(1));
    }

    // --- clearedAt ---

    @Test
    void getRanking_clearedAt은정답접수시각_채점완료시각아님() throws Exception {

        // given: 접수 100초, 채점은 400초에 끝남
        Gate gate = new Gate();
        fakeLlmClient.enqueue(FakeLlmScenario.gated(gate, FakeLlmScenario.respond(allMetGrading())));
        keepAliveUntil(roomId, atElapsed(100_000L));
        Future<Object> pending = submit(() -> bodyOf(mockMvc.perform(accuseButler(newRequestId()))));
        assertThat(gate.awaitEntered(WAIT)).isTrue();
        keepAliveUntil(roomId, atElapsed(400_000L));
        gate.open();
        pending.get();
        assertThat(findPlayResultList(roomId)).singleElement()
                .satisfies(playResult -> assertThat(playResult.getEndedAt()).isEqualTo(atElapsed(400_000L)));

        // when
        ResultActions result = mockMvc.perform(rankingRequest(user));

        // then
        result.andExpect(jsonPath("$.data.myRanking.clearedAt").value("2026-11-12T14:01:40"))
                .andExpect(jsonPath("$.data.myRanking.durationSec").value(100))
                .andExpect(jsonPath("$.data.rankingPage.content[0].clearedAt").value("2026-11-12T14:01:40"));
    }

    @Test
    void getRanking_정답지목2건_행1개_이른접수시각() throws Exception {

        // given: 데이터 오류
        saveClearedRecord(user, 300, DAY2, DAY1.plusMinutes(5), DAY1.plusMinutes(2));

        // when
        ResultActions result = mockMvc.perform(rankingRequest(user));

        // then
        result.andExpect(jsonPath("$.data.rankingPage.totalElements").value(1))
                .andExpect(jsonPath("$.data.rankingPage.content.length()").value(1))
                .andExpect(jsonPath("$.data.myRanking.clearedAt").value("2026-11-13T14:02:00"));
    }

    @Test
    void getRanking_정답지목0건_누락하지않고종료시각으로대신() throws Exception {

        // given: 데이터 오류
        saveClearedRecord(user, 300, DAY2);

        // when
        ResultActions result = mockMvc.perform(rankingRequest(user));

        // then
        result.andExpect(jsonPath("$.data.rankingPage.totalElements").value(1))
                .andExpect(jsonPath("$.data.myRanking.clearedAt").value("2026-11-14T14:00:00"));
    }

    // --- 접근 ---

    @Test
    void getRanking_비공개_없는사건_CASE_NOT_FOUND() throws Exception {

        // given
        Long draftCaseId = transactionTemplate.execute(transaction -> {
            MysteryCase draft = new MysteryCase(T0);
            entityManager.persist(draft);
            return draft.getId();
        });

        // when
        ResultActions draft = mockMvc.perform(rankingRequest(user, draftCaseId));
        ResultActions missing = mockMvc.perform(rankingRequest(user, 999_999_999L));

        // then
        expectError(draft, ErrorCode.CASE_NOT_FOUND);
        expectError(missing, ErrorCode.CASE_NOT_FOUND);
    }

    @Test
    void getRanking_비로그인_401() throws Exception {

        // when
        ResultActions result = mockMvc.perform(get("/api/rankings/{caseId}", sample.mysteryCase().getId()));

        // then
        expectError(result, ErrorCode.LOGIN_REQUIRED);
    }

    // --- 준비 ---

    private User saveAdmin() {
        return transactionTemplate.execute(transaction -> {
            User admin = new User("google-admin-" + newRequestId(), UserRole.ADMIN);
            admin.updateNickname("Ad" + newRequestId().substring(0, 8));
            entityManager.persist(admin);
            return admin;
        });
    }

    // startAt에 시작해 elapsedMs에 정답을 접수한 세션
    private void clearRoom(User owner, LocalDateTime startAt, long elapsedMs) throws Exception {
        Long targetRoomId = startRoomAt(owner, startAt, false);
        accuse(owner, targetRoomId, startAt, elapsedMs);
    }

    private Long startRoomAt(User owner, LocalDateTime startAt, boolean test) {
        testClock.fixAt(startAt);
        Long targetRoomId = transactionTemplate.execute(transaction -> {
            User managedUser = entityManager.getReference(User.class, owner.getId());
            MysteryCase mysteryCase = entityManager.getReference(MysteryCase.class, sample.mysteryCase().getId());
            Room room = new Room(mysteryCase, managedUser, test, LIMIT_SEC, startAt);
            entityManager.persist(room);
            entityManager.persist(new RoomParticipant(room, managedUser));
            return room.getId();
        });
        connectionId = start(owner, targetRoomId);
        return targetRoomId;
    }

    // 직전에 startRoomAt으로 시작한 세션에 지목한다 (enqueue가 없으면 Fake 기본 채점 = 정답)
    private void accuse(User owner, Long targetRoomId, LocalDateTime startAt, long elapsedMs) throws Exception {
        keepAliveUntil(targetRoomId, startAt.plusNanos(elapsedMs * 1_000_000L));
        mockMvc.perform(accusationRequest(owner, targetRoomId, connectionId, newRequestId(), sample.butler().getId(), TRICK))
                .andExpect(status().isOk());
    }

    // 성공 결과를 직접 저장한다. correctSubmittedAt 수만큼 정답 지목을 만든다
    private void saveClearedRecord(User owner, int durationSec, LocalDateTime endedAt, LocalDateTime... correctSubmittedAt) {
        transactionTemplate.executeWithoutResult(transaction -> {
            User managedUser = entityManager.getReference(User.class, owner.getId());
            MysteryCase mysteryCase = entityManager.getReference(MysteryCase.class, sample.mysteryCase().getId());
            Room room = new Room(mysteryCase, managedUser, false, LIMIT_SEC, endedAt);
            room.clear(durationSec * 1000L);
            entityManager.persist(room);
            for (LocalDateTime submittedAt : correctSubmittedAt) {
                Accusation accusation = new Accusation(room, managedUser,
                        entityManager.getReference(Suspect.class, sample.butler().getId()),
                        newRequestId(), TRICK, submittedAt, durationSec * 1000L);
                accusation.completeGrading(true, Verdict.CORRECT, List.of(), FEEDBACK);
                entityManager.persist(accusation);
            }
            entityManager.persist(new PlayResult(room, mysteryCase, true, durationSec, 0, endedAt));
        });
    }

    // --- 요청·응답 ---

    private MockHttpServletRequestBuilder rankingRequest(User requester) {
        return rankingRequest(requester, sample.mysteryCase().getId());
    }

    private MockHttpServletRequestBuilder rankingRequest(User requester, Long caseId) {
        return get("/api/rankings/{caseId}", caseId)
                .header(HttpHeaders.AUTHORIZATION, bearer(requester));
    }

    private static List<String> nicknames(JsonNode content) {
        List<String> nicknameList = new ArrayList<>();
        content.forEach(row -> nicknameList.add(row.get("nickname").asString()));
        return nicknameList;
    }

    private static List<Integer> ranks(JsonNode content) {
        List<Integer> rankList = new ArrayList<>();
        content.forEach(row -> rankList.add(row.get("rank").asInt()));
        return rankList;
    }
}
