package com.shelved.domain.evidence.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shelved.IntegrationTestSupport;
import com.shelved.common.entity.Evidence;
import com.shelved.common.entity.EvidenceInspection;
import com.shelved.common.entity.MysteryCase;
import com.shelved.common.entity.Room;
import com.shelved.common.entity.RoomParticipant;
import com.shelved.common.entity.User;
import com.shelved.common.enums.UserRole;
import com.shelved.common.exception.ErrorCode;
import com.shelved.common.jwt.AuthUser;
import com.shelved.common.jwt.JwtProvider;
import com.shelved.domain.room.service.RoomLifecycleService;
import com.shelved.fixture.ClocktowerCaseFixture;
import com.shelved.fixture.ClocktowerCaseFixture.SampleCase;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 물증 조사 (POST /api/rooms/{roomId}/evidences/{evidenceId}/inspect). 제한시간 1200초
 */
class EvidenceIntegrationTest extends IntegrationTestSupport {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 5, 14, 0);
    private static final int LIMIT_SEC = 1200;
    private static final String CONNECTION_HEADER = "X-Connection-Id";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoomLifecycleService roomLifecycleService;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private SampleCase sample;
    private User user;

    @BeforeEach
    void setUp() {
        testClock.fixAt(T0);
        sample = transactionTemplate.execute(status -> ClocktowerCaseFixture.persistPublished(entityManager, T0));
        user = saveUser();
    }

    @Test
    void inspectEvidence_진행중_고정설명반환과last_seen갱신() throws Exception {

        // given
        Long roomId = saveReadyRoom(user);
        String connectionId = start(roomId);
        testClock.advance(Duration.ofSeconds(3));
        Evidence smallBox = sample.smallBox();

        // when
        ResultActions result = mockMvc.perform(inspect(roomId, smallBox.getId()).header(CONNECTION_HEADER, connectionId));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("물증 조사 성공"))
                .andExpect(jsonPath("$.data.evidenceId").value(smallBox.getId()))
                .andExpect(jsonPath("$.data.name").value(smallBox.getName()))
                .andExpect(jsonPath("$.data.description").value(smallBox.getDescription()));
        assertThat(findRoom(roomId).getLastSeenAt()).isEqualTo(T0.plusSeconds(3));
    }

    @Test
    void inspectEvidence_여러번조사_같은설명과이력1건_firstInspectedAt유지() throws Exception {

        // given
        Long roomId = saveReadyRoom(user);
        String connectionId = start(roomId);
        Long evidenceId = sample.smallBox().getId();
        testClock.advance(Duration.ofSeconds(2));
        mockMvc.perform(inspect(roomId, evidenceId).header(CONNECTION_HEADER, connectionId)).andExpect(status().isOk());
        testClock.advance(Duration.ofSeconds(4));

        // when
        ResultActions result = mockMvc.perform(inspect(roomId, evidenceId).header(CONNECTION_HEADER, connectionId));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.description").value(sample.smallBox().getDescription()));
        List<EvidenceInspection> inspectionList = findInspections(roomId);
        assertThat(inspectionList).hasSize(1);
        assertThat(inspectionList.getFirst().getFirstInspectedAt()).isEqualTo(T0.plusSeconds(2));
    }

    @Test
    void inspectEvidence_다른사건의물증_EVIDENCE_NOT_FOUND() throws Exception {

        // given
        SampleCase otherCase = transactionTemplate.execute(status -> ClocktowerCaseFixture.persistPublished(entityManager, T0));
        Long roomId = saveReadyRoom(user);
        String connectionId = start(roomId);

        // when
        ResultActions result = mockMvc.perform(
                inspect(roomId, otherCase.smallBox().getId()).header(CONNECTION_HEADER, connectionId));

        // then
        expectError(result, ErrorCode.EVIDENCE_NOT_FOUND);
        assertThat(findInspections(roomId)).isEmpty();
    }

    @Test
    void inspectEvidence_없는물증_EVIDENCE_NOT_FOUND() throws Exception {

        // given
        Long roomId = saveReadyRoom(user);
        String connectionId = start(roomId);

        // when
        ResultActions result = mockMvc.perform(inspect(roomId, Long.MAX_VALUE).header(CONNECTION_HEADER, connectionId));

        // then
        expectError(result, ErrorCode.EVIDENCE_NOT_FOUND);
    }

    @Test
    void inspectEvidence_헤더없음_CONNECTION_ID_REQUIRED() throws Exception {

        // given
        Long roomId = saveReadyRoom(user);
        start(roomId);

        // when
        ResultActions result = mockMvc.perform(inspect(roomId, sample.smallBox().getId()));

        // then
        expectError(result, ErrorCode.CONNECTION_ID_REQUIRED);
    }

    @Test
    void inspectEvidence_READY_ROOM_NOT_IN_PROGRESS() throws Exception {

        // given
        Long roomId = saveReadyRoom(user);

        // when
        ResultActions result = mockMvc.perform(
                inspect(roomId, sample.smallBox().getId()).header(CONNECTION_HEADER, "any-connection"));

        // then
        expectError(result, ErrorCode.ROOM_NOT_IN_PROGRESS);
    }

    @Test
    void inspectEvidence_PAUSED_ROOM_NOT_IN_PROGRESS() throws Exception {

        // given
        Long roomId = saveReadyRoom(user);
        String connectionId = start(roomId);
        mockMvc.perform(roomPost(roomId, "pause")).andExpect(status().isOk());

        // when
        ResultActions result = mockMvc.perform(
                inspect(roomId, sample.smallBox().getId()).header(CONNECTION_HEADER, connectionId));

        // then
        expectError(result, ErrorCode.ROOM_NOT_IN_PROGRESS);
        assertThat(findInspections(roomId)).isEmpty();
    }

    @Test
    void inspectEvidence_시간소진_ROOM_TIME_EXPIRED() throws Exception {

        // given: 유예 구간(마지막 접속 확인 10초 전)에서 시간이 소진됨
        Long roomId = saveReadyRoom(user);
        String connectionId = start(roomId);
        keepAliveUntil(roomId, T0.plusSeconds(LIMIT_SEC - 5));
        testClock.advance(Duration.ofSeconds(10));

        // when
        ResultActions result = mockMvc.perform(
                inspect(roomId, sample.smallBox().getId()).header(CONNECTION_HEADER, connectionId));

        // then
        expectError(result, ErrorCode.ROOM_TIME_EXPIRED);
        assertThat(findInspections(roomId)).isEmpty();
    }

    @Test
    void inspectEvidence_다른연결_CONNECTION_REPLACED() throws Exception {

        // given
        Long roomId = saveReadyRoom(user);
        start(roomId);

        // when
        ResultActions result = mockMvc.perform(
                inspect(roomId, sample.smallBox().getId()).header(CONNECTION_HEADER, "other-connection"));

        // then
        expectError(result, ErrorCode.CONNECTION_REPLACED);
        assertThat(findInspections(roomId)).isEmpty();
    }

    @Test
    void inspectEvidence_다른사용자세션_ROOM_NOT_FOUND() throws Exception {

        // given
        User otherUser = saveUser();
        Long otherRoomId = saveReadyRoom(otherUser);
        String connectionId = roomLifecycleService.startRoom(authUserOf(otherUser), otherRoomId).connectionId();

        // when
        ResultActions result = mockMvc.perform(
                inspect(otherRoomId, sample.smallBox().getId()).header(CONNECTION_HEADER, connectionId));

        // then
        expectError(result, ErrorCode.ROOM_NOT_FOUND);
        assertThat(findInspections(otherRoomId)).isEmpty();
    }

    // --- 도우미 ---

    private User saveUser() {
        return transactionTemplate.execute(status -> {
            User saved = new User("google-" + UUID.randomUUID(), UserRole.PLAYER);
            saved.updateNickname("Nk" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
            entityManager.persist(saved);
            return saved;
        });
    }

    private Long saveReadyRoom(User owner) {
        return transactionTemplate.execute(status -> {
            User managedUser = entityManager.getReference(User.class, owner.getId());
            MysteryCase mysteryCase = entityManager.getReference(MysteryCase.class, sample.mysteryCase().getId());
            Room room = new Room(mysteryCase, managedUser, false, LIMIT_SEC, T0.minusMinutes(1));
            entityManager.persist(room);
            entityManager.persist(new RoomParticipant(room, managedUser));
            return room.getId();
        });
    }

    // 하트비트를 계속 받은 것처럼 마지막 접속 확인 시각을 옮기고 시계를 그 시각으로 맞춘다
    private void keepAliveUntil(Long roomId, LocalDateTime seenAt) {
        transactionTemplate.executeWithoutResult(status -> entityManager.createQuery(
                        "update Room r set r.lastSeenAt = :seenAt where r.id = :roomId")
                .setParameter("seenAt", seenAt)
                .setParameter("roomId", roomId)
                .executeUpdate());
        testClock.fixAt(seenAt);
    }

    private String start(Long roomId) {
        return roomLifecycleService.startRoom(authUserOf(user), roomId).connectionId();
    }

    private MockHttpServletRequestBuilder inspect(Long roomId, Long evidenceId) {
        return post("/api/rooms/{roomId}/evidences/{evidenceId}/inspect", roomId, evidenceId)
                .header(HttpHeaders.AUTHORIZATION, bearer(user));
    }

    private MockHttpServletRequestBuilder roomPost(Long roomId, String action) {
        return post("/api/rooms/{roomId}/" + action, roomId).header(HttpHeaders.AUTHORIZATION, bearer(user));
    }

    private Room findRoom(Long roomId) {
        return transactionTemplate.execute(status -> entityManager.find(Room.class, roomId));
    }

    private List<EvidenceInspection> findInspections(Long roomId) {
        return transactionTemplate.execute(status -> entityManager.createQuery(
                        "select i from EvidenceInspection i where i.room.id = :roomId", EvidenceInspection.class)
                .setParameter("roomId", roomId)
                .getResultList());
    }

    private static AuthUser authUserOf(User owner) {
        return new AuthUser(owner.getId(), owner.getRole(), false);
    }

    private String bearer(User owner) {
        return "Bearer " + jwtProvider.createAccessToken(owner.getId());
    }

    private static void expectError(ResultActions result, ErrorCode errorCode) throws Exception {
        result.andExpect(status().is(errorCode.getStatus().value()))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.code").value(errorCode.name()));
    }
}
