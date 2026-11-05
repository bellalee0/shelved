package com.shelved.domain.room.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shelved.IntegrationTestSupport;
import com.shelved.common.entity.Accusation;
import com.shelved.common.entity.MysteryCase;
import com.shelved.common.entity.PlayResult;
import com.shelved.common.entity.PlayerNote;
import com.shelved.common.entity.Room;
import com.shelved.common.entity.RoomParticipant;
import com.shelved.common.entity.Suspect;
import com.shelved.common.entity.User;
import com.shelved.common.enums.Difficulty;
import com.shelved.common.enums.RoomStatus;
import com.shelved.common.enums.UserRole;
import com.shelved.common.exception.CustomException;
import com.shelved.common.exception.ErrorCode;
import com.shelved.common.jwt.AuthUser;
import com.shelved.common.jwt.JwtProvider;
import com.shelved.domain.room.dto.RoomCreateRequest;
import com.shelved.domain.room.service.RoomService;
import com.shelved.fixture.ClocktowerCaseFixture;
import com.shelved.fixture.ClocktowerCaseFixture.SampleCase;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

class RoomIntegrationTest extends IntegrationTestSupport {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 5, 14, 0);
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoomService roomService;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private SampleCase sample;
    private Long caseId;

    @BeforeEach
    void setUp() {
        sample = transactionTemplate.execute(status -> ClocktowerCaseFixture.persistPublished(entityManager, NOW));
        caseId = sample.mysteryCase().getId();
    }

    // --- POST /api/rooms ---

    @Test
    void createRoom_성공_READY세션과참가자와빈노트생성() throws Exception {

        // given
        User user = saveUser(UserRole.PLAYER);

        // when
        ResultActions result = mockMvc.perform(createRoomRequest(user, caseId));

        // then
        result.andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("세션 생성 성공"))
                .andExpect(jsonPath("$.data.caseId").value(caseId))
                .andExpect(jsonPath("$.data.status").value("READY"));
        Long roomId = readRoomId(result);
        transactionTemplate.executeWithoutResult(status -> {
            Room room = entityManager.find(Room.class, roomId);
            assertThat(room.getTimeLimitSec()).isEqualTo(1200);
            assertThat(room.isTest()).isFalse();
            assertThat(room.getHostUser().getId()).isEqualTo(user.getId());

            List<RoomParticipant> participants = entityManager.createQuery(
                            "select p from RoomParticipant p where p.room.id = :roomId", RoomParticipant.class)
                    .setParameter("roomId", roomId).getResultList();
            assertThat(participants).extracting(p -> p.getUser().getId()).containsExactly(user.getId());

            List<PlayerNote> notes = entityManager.createQuery(
                            "select n from PlayerNote n where n.room.id = :roomId", PlayerNote.class)
                    .setParameter("roomId", roomId).getResultList();
            assertThat(notes).singleElement().satisfies(note -> {
                assertThat(note.getUser().getId()).isEqualTo(user.getId());
                assertThat(note.getContent()).isEmpty();
                assertThat(note.getRevision()).isZero();
            });
        });
    }

    @Test
    void createRoom_비공개사건또는없는사건_CASE_NOT_FOUND() throws Exception {

        // given
        User user = saveUser(UserRole.PLAYER);
        Long draftCaseId = transactionTemplate.execute(status -> {
            MysteryCase draft = new MysteryCase(NOW);
            draft.updateCardInfo("초안 사건", Difficulty.EASY, List.of());
            entityManager.persist(draft);
            return draft.getId();
        });

        // when
        ResultActions draftResult = mockMvc.perform(createRoomRequest(user, draftCaseId));
        ResultActions missingResult = mockMvc.perform(createRoomRequest(user, Long.MAX_VALUE));

        // then
        expectError(draftResult, ErrorCode.CASE_NOT_FOUND);
        expectError(missingResult, ErrorCode.CASE_NOT_FOUND);
    }

    @Test
    void createRoom_미종료세션존재_ROOM_ALREADY_EXISTS와기존roomId() throws Exception {

        // given
        User user = saveUser(UserRole.PLAYER);
        Long existingRoomId = readRoomId(mockMvc.perform(createRoomRequest(user, caseId)));
        updateRoomStatus(existingRoomId, RoomStatus.PAUSED, 60_000L);

        // when
        ResultActions result = mockMvc.perform(createRoomRequest(user, caseId));

        // then
        expectError(result, ErrorCode.ROOM_ALREADY_EXISTS)
                .andExpect(jsonPath("$.data.detail.roomId").value(existingRoomId));
    }

    @Test
    void createRoom_종료세션과테스트세션만있음_새로생성() throws Exception {

        // given
        User user = saveUser(UserRole.ADMIN);
        Long endedRoomId = readRoomId(mockMvc.perform(createRoomRequest(user, caseId)));
        updateRoomStatus(endedRoomId, RoomStatus.EXPIRED, 1_200_000L);
        saveRoom(user, true, NOW);

        // when
        ResultActions result = mockMvc.perform(createRoomRequest(user, caseId));

        // then
        result.andExpect(status().isCreated());
        assertThat(readRoomId(result)).isNotEqualTo(endedRoomId);
    }

    @Test
    void createRoom_다른사건의미종료세션_영향없음() throws Exception {

        // given
        User user = saveUser(UserRole.PLAYER);
        Long otherCaseId = transactionTemplate.execute(status ->
                ClocktowerCaseFixture.persistPublished(entityManager, NOW).mysteryCase().getId());
        mockMvc.perform(createRoomRequest(user, otherCaseId)).andExpect(status().isCreated());

        // when
        ResultActions result = mockMvc.perform(createRoomRequest(user, caseId));

        // then
        result.andExpect(status().isCreated());
    }

    @Test
    void createRoom_같은사건동시요청_미종료세션1개() throws Exception {

        // given
        User user = saveUser(UserRole.PLAYER);
        AuthUser authUser = AuthUser.from(user);
        int threadCount = 5;
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        Callable<ErrorCode> task = () -> {
            ready.countDown();
            start.await();
            try {
                roomService.createRoom(authUser, new RoomCreateRequest(caseId));
                return null;
            } catch (CustomException e) {
                return e.getErrorCode();
            }
        };

        // when
        List<ErrorCode> results = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            List<Future<ErrorCode>> futures = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(task));
            }
            ready.await();
            start.countDown();
            for (Future<ErrorCode> future : futures) {
                results.add(future.get());
            }
        }

        // then
        assertThat(results).filteredOn(code -> code == null).hasSize(1);
        assertThat(results).filteredOn(code -> code != null)
                .hasSize(threadCount - 1)
                .containsOnly(ErrorCode.ROOM_ALREADY_EXISTS);
        Long roomCount = transactionTemplate.execute(status -> entityManager.createQuery(
                        "select count(r) from Room r where r.hostUser.id = :userId and r.mysteryCase.id = :caseId", Long.class)
                .setParameter("userId", user.getId())
                .setParameter("caseId", caseId)
                .getSingleResult());
        assertThat(roomCount).isEqualTo(1L);
    }

    // --- GET /api/rooms ---

    @Test
    void getRoomPage_상태필터_최신순_테스트세션과다른사용자세션제외() throws Exception {

        // given
        User user = saveUser(UserRole.ADMIN);
        User other = saveUser(UserRole.PLAYER);
        Long readyRoomId = saveRoom(user, false, NOW.minusHours(3));
        Long pausedRoomId = saveRoom(user, false, NOW.minusHours(2));
        Long expiredRoomId = saveRoom(user, false, NOW.minusHours(1));
        saveRoom(user, true, NOW);
        saveRoom(other, false, NOW);
        updateRoomStatus(pausedRoomId, RoomStatus.PAUSED, 60_000L);
        updateRoomStatus(expiredRoomId, RoomStatus.EXPIRED, 1_200_000L);
        LocalDateTime endedAt = NOW.minusMinutes(30);
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.persist(new PlayResult(entityManager.getReference(Room.class, expiredRoomId),
                    entityManager.getReference(MysteryCase.class, caseId), false, 1200, 2, endedAt));
            entityManager.persist(new Accusation(entityManager.getReference(Room.class, readyRoomId),
                    entityManager.getReference(User.class, user.getId()),
                    entityManager.getReference(Suspect.class, sample.butler().getId()),
                    UUID.randomUUID().toString(), "가상 답안", NOW.minusHours(2), 0L));
        });

        // when
        ResultActions allResult = mockMvc.perform(get("/api/rooms").header(HttpHeaders.AUTHORIZATION, bearer(user)));
        ResultActions activeResult = mockMvc.perform(get("/api/rooms").param("status", "ACTIVE")
                .header(HttpHeaders.AUTHORIZATION, bearer(user)));
        ResultActions endedResult = mockMvc.perform(get("/api/rooms").param("status", "ENDED")
                .header(HttpHeaders.AUTHORIZATION, bearer(user)));

        // then
        allResult.andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("세션 목록 조회 성공"))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.number").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.content[*].roomId").value(contains(
                        expiredRoomId.intValue(), pausedRoomId.intValue(), readyRoomId.intValue())));
        activeResult.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].roomId").value(contains(
                        pausedRoomId.intValue(), readyRoomId.intValue())))
                .andExpect(jsonPath("$.data.content[0].status").value("PAUSED"))
                .andExpect(jsonPath("$.data.content[0].remainingMs").value(1_140_000))
                .andExpect(jsonPath("$.data.content[0].pendingGradingStatus").isEmpty())
                .andExpect(jsonPath("$.data.content[1].remainingMs").value(1_200_000))
                .andExpect(jsonPath("$.data.content[1].pendingGradingStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.content[1].endedAt").isEmpty())
                .andExpect(jsonPath("$.data.content[1].durationSec").isEmpty())
                .andExpect(jsonPath("$.data.content[1].caseId").value(caseId))
                .andExpect(jsonPath("$.data.content[1].displayTitle").value(ClocktowerCaseFixture.DISPLAY_TITLE))
                .andExpect(jsonPath("$.data.content[1].difficulty").value("EASY"))
                .andExpect(jsonPath("$.data.content[1].coverImageUrl").value(ClocktowerCaseFixture.COVER_IMAGE_URL))
                .andExpect(jsonPath("$.data.content[1].createdAt").value("2026-11-05T11:00:00"));
        endedResult.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].roomId").value(expiredRoomId))
                .andExpect(jsonPath("$.data.content[0].status").value("EXPIRED"))
                .andExpect(jsonPath("$.data.content[0].remainingMs").value(0))
                .andExpect(jsonPath("$.data.content[0].endedAt").value("2026-11-05T13:30:00"))
                .andExpect(jsonPath("$.data.content[0].durationSec").value(1200));
    }

    @Test
    void getRoomPage_잘못된상태필터_INVALID_PARAMETER() throws Exception {

        // given
        User user = saveUser(UserRole.PLAYER);

        // when
        ResultActions result = mockMvc.perform(get("/api/rooms").param("status", "READY")
                .header(HttpHeaders.AUTHORIZATION, bearer(user)));

        // then
        expectError(result, ErrorCode.INVALID_PARAMETER);
    }

    // --- GET /api/rooms/{roomId}/briefing ---

    @Test
    void getRoomBriefing_성공_사전정보와설정값() throws Exception {

        // given
        User user = saveUser(UserRole.PLAYER);
        Long roomId = saveRoom(user, false, NOW);

        // when
        ResultActions result = mockMvc.perform(get("/api/rooms/{roomId}/briefing", roomId)
                .header(HttpHeaders.AUTHORIZATION, bearer(user)));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("사전 정보 조회 성공"))
                .andExpect(jsonPath("$.data.displayTitle").value(ClocktowerCaseFixture.DISPLAY_TITLE))
                .andExpect(jsonPath("$.data.difficulty").value("EASY"))
                .andExpect(jsonPath("$.data.introduction").value(ClocktowerCaseFixture.INTRODUCTION))
                .andExpect(jsonPath("$.data.investigationGoal").value(ClocktowerCaseFixture.INVESTIGATION_GOAL))
                .andExpect(jsonPath("$.data.backgroundKnowledge").value(ClocktowerCaseFixture.BACKGROUND_KNOWLEDGE))
                .andExpect(jsonPath("$.data.timeLimitSec").value(1200))
                .andExpect(jsonPath("$.data.hintLimit").value(5))
                .andExpect(jsonPath("$.data.accusationLimit").value(3));
        assertThat(result.andReturn().getResponse().getContentAsString()).doesNotContain(
                ClocktowerCaseFixture.SOURCE_NOVEL_TITLE,
                ClocktowerCaseFixture.TRICK_SUMMARY,
                ClocktowerCaseFixture.REVEAL_EXPLANATION,
                "glossary");
    }

    @Test
    void getRoomBriefing_다른사용자세션또는없는세션_ROOM_NOT_FOUND() throws Exception {

        // given
        User owner = saveUser(UserRole.PLAYER);
        User other = saveUser(UserRole.ADMIN);
        Long roomId = saveRoom(owner, false, NOW);

        // when
        ResultActions otherResult = mockMvc.perform(get("/api/rooms/{roomId}/briefing", roomId)
                .header(HttpHeaders.AUTHORIZATION, bearer(other)));
        ResultActions missingResult = mockMvc.perform(get("/api/rooms/{roomId}/briefing", Long.MAX_VALUE)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)));

        // then
        expectError(otherResult, ErrorCode.ROOM_NOT_FOUND);
        expectError(missingResult, ErrorCode.ROOM_NOT_FOUND);
    }

    @Test
    void getRoomBriefing_테스트세션_관리자가아니면ROOM_NOT_FOUND() throws Exception {

        // given: 참가자인 일반 사용자의 테스트 세션과 관리자의 테스트 세션
        User player = saveUser(UserRole.PLAYER);
        User admin = saveUser(UserRole.ADMIN);
        Long playerTestRoomId = saveRoom(player, true, NOW);
        Long adminTestRoomId = saveRoom(admin, true, NOW);

        // when
        ResultActions playerResult = mockMvc.perform(get("/api/rooms/{roomId}/briefing", playerTestRoomId)
                .header(HttpHeaders.AUTHORIZATION, bearer(player)));
        ResultActions adminResult = mockMvc.perform(get("/api/rooms/{roomId}/briefing", adminTestRoomId)
                .header(HttpHeaders.AUTHORIZATION, bearer(admin)));

        // then
        expectError(playerResult, ErrorCode.ROOM_NOT_FOUND);
        adminResult.andExpect(status().isOk());
    }

    // --- GET /api/rooms/{roomId}/map ---

    @Test
    void getRoomMap_성공_모든인물과배치된물증_미배치인물좌표null() throws Exception {

        // given
        User user = saveUser(UserRole.PLAYER);
        Long roomId = saveRoom(user, false, NOW);
        updateRoomStatus(roomId, RoomStatus.CLEARED, 600_000L);

        // when
        ResultActions result = mockMvc.perform(get("/api/rooms/{roomId}/map", roomId)
                .header(HttpHeaders.AUTHORIZATION, bearer(user)));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("맵 정보 조회 성공"))
                .andExpect(jsonPath("$.data.mapUrl").value(ClocktowerCaseFixture.MAP_URL))
                .andExpect(jsonPath("$.data.spawnX").value(64))
                .andExpect(jsonPath("$.data.spawnY").value(64))
                .andExpect(jsonPath("$.data.collisionLayer").value(ClocktowerCaseFixture.COLLISION_LAYER))
                .andExpect(jsonPath("$.data.interactionRadiusPx").value(48))
                .andExpect(jsonPath("$.data.suspects.length()").value(4))
                .andExpect(jsonPath("$.data.suspects[0].suspectId").value(sample.butler().getId()))
                .andExpect(jsonPath("$.data.suspects[0].name").value("해리스"))
                .andExpect(jsonPath("$.data.suspects[0].spriteKey").value("npc-butler-01"))
                .andExpect(jsonPath("$.data.suspects[0].canConverse").value(true))
                .andExpect(jsonPath("$.data.suspects[0].x").value(320))
                .andExpect(jsonPath("$.data.suspects[0].y").value(192))
                .andExpect(jsonPath("$.data.suspects[3].suspectId").value(sample.doctor().getId()))
                .andExpect(jsonPath("$.data.suspects[3].canConverse").value(false))
                .andExpect(jsonPath("$.data.suspects[3].spriteKey").isEmpty())
                .andExpect(jsonPath("$.data.suspects[3].x").isEmpty())
                .andExpect(jsonPath("$.data.suspects[3].y").isEmpty())
                .andExpect(jsonPath("$.data.evidences.length()").value(2))
                .andExpect(jsonPath("$.data.evidences[0].evidenceId").value(sample.smallBox().getId()))
                .andExpect(jsonPath("$.data.evidences[0].name").value("서재의 작은 상자"))
                .andExpect(jsonPath("$.data.evidences[0].x").value(160))
                .andExpect(jsonPath("$.data.evidences[0].y").value(128));
        String body = result.andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(
                "description", "culprit", "persona", "태엽 장치", ClocktowerCaseFixture.TRICK_SUMMARY);
    }

    @Test
    void getRoomMap_다른사용자세션_ROOM_NOT_FOUND() throws Exception {

        // given
        User owner = saveUser(UserRole.PLAYER);
        User other = saveUser(UserRole.PLAYER);
        Long roomId = saveRoom(owner, false, NOW);

        // when
        ResultActions result = mockMvc.perform(get("/api/rooms/{roomId}/map", roomId)
                .header(HttpHeaders.AUTHORIZATION, bearer(other)));

        // then
        expectError(result, ErrorCode.ROOM_NOT_FOUND);
    }

    @Test
    void getRoomMap_테스트세션_관리자가아니면ROOM_NOT_FOUND() throws Exception {

        // given
        User player = saveUser(UserRole.PLAYER);
        Long roomId = saveRoom(player, true, NOW);

        // when
        ResultActions result = mockMvc.perform(get("/api/rooms/{roomId}/map", roomId)
                .header(HttpHeaders.AUTHORIZATION, bearer(player)));

        // then
        expectError(result, ErrorCode.ROOM_NOT_FOUND);
    }

    // --- 도우미 ---

    private User saveUser(UserRole role) {
        return transactionTemplate.execute(status -> {
            User user = new User("google-" + UUID.randomUUID(), role);
            user.updateNickname("Nk" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
            entityManager.persist(user);
            return user;
        });
    }

    // 상태 전이 API(BE-5) 없이 목록·접근 검사를 확인하기 위해 세션·참가자를 직접 저장한다
    private Long saveRoom(User user, boolean test, LocalDateTime createdAt) {
        return transactionTemplate.execute(status -> {
            User managedUser = entityManager.getReference(User.class, user.getId());
            MysteryCase mysteryCase = entityManager.getReference(MysteryCase.class, caseId);
            Room room = new Room(mysteryCase, managedUser, test, 1200, createdAt);
            entityManager.persist(room);
            entityManager.persist(new RoomParticipant(room, managedUser));
            return room.getId();
        });
    }

    private void updateRoomStatus(Long roomId, RoomStatus roomStatus, long elapsedActiveMs) {
        transactionTemplate.executeWithoutResult(status -> entityManager.createQuery(
                        "update Room r set r.status = :status, r.elapsedActiveMs = :elapsed where r.id = :roomId")
                .setParameter("status", roomStatus)
                .setParameter("elapsed", elapsedActiveMs)
                .setParameter("roomId", roomId)
                .executeUpdate());
    }

    private RequestBuilder createRoomRequest(User user, Long targetCaseId) {
        return post("/api/rooms")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"caseId\": " + targetCaseId + "}");
    }

    private Long readRoomId(ResultActions result) throws Exception {
        String body = result.andReturn().getResponse().getContentAsString();
        return JSON_MAPPER.readTree(body).get("data").get("roomId").asLong();
    }

    private String bearer(User user) {
        return "Bearer " + jwtProvider.createAccessToken(user.getId());
    }

    private static ResultActions expectError(ResultActions result, ErrorCode errorCode) throws Exception {
        return result.andExpect(status().is(errorCode.getStatus().value()))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.code").value(errorCode.name()));
    }
}
