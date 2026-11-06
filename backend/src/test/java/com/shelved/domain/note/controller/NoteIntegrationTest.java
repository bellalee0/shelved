package com.shelved.domain.note.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shelved.IntegrationTestSupport;
import com.shelved.common.config.NoteProperties;
import com.shelved.common.entity.MysteryCase;
import com.shelved.common.entity.PlayerNote;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * 개인 노트 조회·저장 (GET·PUT /api/rooms/{roomId}/notes/me). 제한시간 1200초, 이탈 기준 15초
 */
class NoteIntegrationTest extends IntegrationTestSupport {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 5, 14, 0);
    private static final int LIMIT_SEC = 1200;
    private static final String CONNECTION_HEADER = "X-Connection-Id";
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoomLifecycleService roomLifecycleService;

    @Autowired
    private NoteProperties noteProperties;

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

    // --- GET notes/me ---

    @Test
    void getMyNote_새세션_빈내용과revision0_헤더없이조회() throws Exception {

        // given
        Long roomId = saveReadyRoom(user);

        // when
        ResultActions result = mockMvc.perform(noteGet(roomId));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("노트 조회 성공"))
                .andExpect(jsonPath("$.data.content").value(""))
                .andExpect(jsonPath("$.data.revision").value(0))
                .andExpect(jsonPath("$.data.updatedAt").exists())
                .andExpect(jsonPath("$.data.maxLength").value(noteProperties.maxLength()))
                .andExpect(jsonPath("$.data.editable").value(false));
    }

    @Test
    void getMyNote_IN_PROGRESS_editable() throws Exception {

        // given
        Long roomId = saveReadyRoom(user);
        start(roomId);

        // when
        ResultActions result = mockMvc.perform(noteGet(roomId));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.editable").value(true));
    }

    @Test
    void getMyNote_PAUSED_editable아님() throws Exception {

        // given
        Long roomId = saveReadyRoom(user);
        start(roomId);
        mockMvc.perform(roomPost(roomId, "pause")).andExpect(status().isOk());

        // when
        ResultActions result = mockMvc.perform(noteGet(roomId));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.editable").value(false));
    }

    @Test
    void getMyNote_유예구간에서시간소진_editable아님과상태유지() throws Exception {

        // given
        Long roomId = saveReadyRoom(user);
        start(roomId);
        keepAliveUntil(roomId, T0.plusSeconds(LIMIT_SEC - 5));
        testClock.advance(Duration.ofSeconds(10));

        // when
        ResultActions result = mockMvc.perform(noteGet(roomId));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.editable").value(false));
        assertThat(findRoom(roomId).getStatus().name()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void getMyNote_종료_editable아님() throws Exception {

        // given
        Long roomId = saveReadyRoom(user);
        String connectionId = start(roomId);
        expire(roomId, connectionId);

        // when
        ResultActions result = mockMvc.perform(noteGet(roomId));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.editable").value(false));
    }

    @Test
    void getMyNote_다른사용자세션_ROOM_NOT_FOUND() throws Exception {

        // given
        Long otherRoomId = saveReadyRoom(saveUser());

        // when
        ResultActions result = mockMvc.perform(noteGet(otherRoomId));

        // then
        expectError(result, ErrorCode.ROOM_NOT_FOUND);
    }

    // --- PUT notes/me ---

    @Test
    void updateMyNote_revision일치_저장과revision증가() throws Exception {

        // given
        Long roomId = saveReadyRoom(user);
        String connectionId = start(roomId);
        testClock.advance(Duration.ofSeconds(3));

        // when
        ResultActions result = mockMvc.perform(notePut(roomId, connectionId, "첫 메모", 0));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("노트 저장 성공"))
                .andExpect(jsonPath("$.data.content").value("첫 메모"))
                .andExpect(jsonPath("$.data.revision").value(1))
                .andExpect(jsonPath("$.data.maxLength").value(noteProperties.maxLength()))
                .andExpect(jsonPath("$.data.editable").value(true));
        PlayerNote note = findNote(roomId);
        assertThat(note.getRevision()).isEqualTo(1);
        assertThat(note.getUpdatedAt()).isEqualTo(T0.plusSeconds(3));
        assertThat(findRoom(roomId).getLastSeenAt()).isEqualTo(T0.plusSeconds(3));
    }

    @Test
    void updateMyNote_공백과줄바꿈_원문그대로저장() throws Exception {

        // given
        Long roomId = saveReadyRoom(user);
        String connectionId = start(roomId);
        String content = "  \t앞뒤 공백\r\n둘째 줄\n　 ";

        // when
        ResultActions result = mockMvc.perform(notePut(roomId, connectionId, content, 0));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value(content));
        assertThat(findNote(roomId).getContent()).isEqualTo(content);
    }

    @Test
    void updateMyNote_빈문자열_저장() throws Exception {

        // given
        Long roomId = saveReadyRoom(user);
        String connectionId = start(roomId);
        mockMvc.perform(notePut(roomId, connectionId, "지울 내용", 0)).andExpect(status().isOk());

        // when
        ResultActions result = mockMvc.perform(notePut(roomId, connectionId, "", 1));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value(""))
                .andExpect(jsonPath("$.data.revision").value(2));
        assertThat(findNote(roomId).getContent()).isEmpty();
    }

    @Test
    void updateMyNote_최대글자수_저장() throws Exception {

        // given
        Long roomId = saveReadyRoom(user);
        String connectionId = start(roomId);
        String content = "가".repeat(noteProperties.maxLength());

        // when
        ResultActions result = mockMvc.perform(notePut(roomId, connectionId, content, 0));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revision").value(1));
        assertThat(findNote(roomId).getContent()).hasSize(noteProperties.maxLength());
    }

    @Test
    void updateMyNote_최대글자수초과_INVALID_INPUT() throws Exception {

        // given
        Long roomId = saveReadyRoom(user);
        String connectionId = start(roomId);
        String content = "가".repeat(noteProperties.maxLength() + 1);

        // when
        ResultActions result = mockMvc.perform(notePut(roomId, connectionId, content, 0));

        // then
        expectError(result, ErrorCode.INVALID_INPUT);
        result.andExpect(jsonPath("$.data.detail.field").value("content"));
        assertThat(findNote(roomId).getRevision()).isZero();
    }

    @Test
    void updateMyNote_content누락_INVALID_INPUT() throws Exception {

        // given
        Long roomId = saveReadyRoom(user);
        String connectionId = start(roomId);
        Map<String, Object> body = new HashMap<>();
        body.put("revision", 0);

        // when
        ResultActions result = mockMvc.perform(notePutBody(roomId, connectionId, body));

        // then
        expectError(result, ErrorCode.INVALID_INPUT);
        result.andExpect(jsonPath("$.data.detail.field").value("content"));
    }

    @Test
    void updateMyNote_revision음수_INVALID_INPUT() throws Exception {

        // given
        Long roomId = saveReadyRoom(user);
        String connectionId = start(roomId);

        // when
        ResultActions result = mockMvc.perform(notePut(roomId, connectionId, "메모", -1));

        // then
        expectError(result, ErrorCode.INVALID_INPUT);
        result.andExpect(jsonPath("$.data.detail.field").value("revision"));
    }

    @Test
    void updateMyNote_오래된revision_NOTE_REVISION_CONFLICT와서버내용() throws Exception {

        // given
        Long roomId = saveReadyRoom(user);
        String connectionId = start(roomId);
        mockMvc.perform(notePut(roomId, connectionId, "서버에 먼저 저장된 내용", 0)).andExpect(status().isOk());

        // when
        ResultActions result = mockMvc.perform(notePut(roomId, connectionId, "다른 기기의 내용", 0));

        // then
        expectError(result, ErrorCode.NOTE_REVISION_CONFLICT);
        result.andExpect(jsonPath("$.data.detail.currentRevision").value(1))
                .andExpect(jsonPath("$.data.detail.currentContent").value("서버에 먼저 저장된 내용"));
        PlayerNote note = findNote(roomId);
        assertThat(note.getRevision()).isEqualTo(1);
        assertThat(note.getContent()).isEqualTo("서버에 먼저 저장된 내용");
    }

    @Test
    void updateMyNote_PAUSED_ROOM_NOT_IN_PROGRESS() throws Exception {

        // given
        Long roomId = saveReadyRoom(user);
        String connectionId = start(roomId);
        mockMvc.perform(roomPost(roomId, "pause")).andExpect(status().isOk());

        // when
        ResultActions result = mockMvc.perform(notePut(roomId, connectionId, "일시정지 후 메모", 0));

        // then
        expectError(result, ErrorCode.ROOM_NOT_IN_PROGRESS);
        assertThat(findNote(roomId).getRevision()).isZero();
    }

    @Test
    void updateMyNote_종료후_ROOM_NOT_IN_PROGRESS() throws Exception {

        // given
        Long roomId = saveReadyRoom(user);
        String connectionId = start(roomId);
        expire(roomId, connectionId);

        // when
        ResultActions result = mockMvc.perform(notePut(roomId, connectionId, "종료 후 메모", 0));

        // then
        expectError(result, ErrorCode.ROOM_NOT_IN_PROGRESS);
        assertThat(findNote(roomId).getRevision()).isZero();
    }

    @Test
    void updateMyNote_다른사용자세션_ROOM_NOT_FOUND() throws Exception {

        // given
        User otherUser = saveUser();
        Long otherRoomId = saveReadyRoom(otherUser);
        String connectionId = roomLifecycleService.startRoom(authUserOf(otherUser), otherRoomId).connectionId();

        // when
        ResultActions result = mockMvc.perform(notePut(otherRoomId, connectionId, "남의 노트", 0));

        // then
        expectError(result, ErrorCode.ROOM_NOT_FOUND);
        assertThat(findNote(otherRoomId).getRevision()).isZero();
    }

    // --- 동시 요청 ---

    @Test
    void updateMyNote_같은revision동시2건_1건성공1건충돌() throws Exception {

        // given
        Long roomId = saveReadyRoom(user);
        String connectionId = start(roomId);

        // when
        List<ResultActions> resultList = runConcurrently(
                () -> mockMvc.perform(notePut(roomId, connectionId, "기기 A", 0)),
                () -> mockMvc.perform(notePut(roomId, connectionId, "기기 B", 0)));

        // then
        List<Integer> statusList = new ArrayList<>();
        for (ResultActions result : resultList) {
            statusList.add(result.andReturn().getResponse().getStatus());
        }
        assertThat(statusList).containsExactlyInAnyOrder(200, 409);
        ResultActions conflict = resultList.get(statusList.indexOf(409));
        expectError(conflict, ErrorCode.NOTE_REVISION_CONFLICT);
        assertThat(findNote(roomId).getRevision()).isEqualTo(1);
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

    // 세션 생성 API와 같이 빈 노트(revision 0)를 함께 만든다
    private Long saveReadyRoom(User owner) {
        return transactionTemplate.execute(status -> {
            User managedUser = entityManager.getReference(User.class, owner.getId());
            MysteryCase mysteryCase = entityManager.getReference(MysteryCase.class, sample.mysteryCase().getId());
            Room room = new Room(mysteryCase, managedUser, false, LIMIT_SEC, T0.minusMinutes(1));
            entityManager.persist(room);
            entityManager.persist(new RoomParticipant(room, managedUser));
            entityManager.persist(new PlayerNote(room, managedUser, T0.minusMinutes(1)));
            return room.getId();
        });
    }

    private void keepAliveUntil(Long roomId, LocalDateTime seenAt) {
        transactionTemplate.executeWithoutResult(status -> entityManager.createQuery(
                        "update Room r set r.lastSeenAt = :seenAt where r.id = :roomId")
                .setParameter("seenAt", seenAt)
                .setParameter("roomId", roomId)
                .executeUpdate());
        testClock.fixAt(seenAt);
    }

    // 마지막 접속 확인 직후 시간이 소진된 상태에서 하트비트를 보내 EXPIRED로 만든다
    private void expire(Long roomId, String connectionId) throws Exception {
        keepAliveUntil(roomId, T0.plusSeconds(LIMIT_SEC - 1));
        testClock.advance(Duration.ofSeconds(2));
        mockMvc.perform(roomPost(roomId, "heartbeat").header(CONNECTION_HEADER, connectionId))
                .andExpect(jsonPath("$.data.status").value("EXPIRED"));
    }

    private String start(Long roomId) {
        return roomLifecycleService.startRoom(authUserOf(user), roomId).connectionId();
    }

    private MockHttpServletRequestBuilder noteGet(Long roomId) {
        return get("/api/rooms/{roomId}/notes/me", roomId).header(HttpHeaders.AUTHORIZATION, bearer(user));
    }

    private MockHttpServletRequestBuilder notePut(Long roomId, String connectionId, String content, int revision) {
        Map<String, Object> body = new HashMap<>();
        body.put("content", content);
        body.put("revision", revision);
        return notePutBody(roomId, connectionId, body);
    }

    private MockHttpServletRequestBuilder notePutBody(Long roomId, String connectionId, Map<String, Object> body) {
        return put("/api/rooms/{roomId}/notes/me", roomId)
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .header(CONNECTION_HEADER, connectionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON_MAPPER.writeValueAsString(body));
    }

    private MockHttpServletRequestBuilder roomPost(Long roomId, String action) {
        return post("/api/rooms/{roomId}/" + action, roomId).header(HttpHeaders.AUTHORIZATION, bearer(user));
    }

    private Room findRoom(Long roomId) {
        return transactionTemplate.execute(status -> entityManager.find(Room.class, roomId));
    }

    private PlayerNote findNote(Long roomId) {
        return transactionTemplate.execute(status -> entityManager.createQuery(
                        "select n from PlayerNote n where n.room.id = :roomId", PlayerNote.class)
                .setParameter("roomId", roomId)
                .getSingleResult());
    }

    private static AuthUser authUserOf(User owner) {
        return new AuthUser(owner.getId(), owner.getRole(), false);
    }

    private String bearer(User owner) {
        return "Bearer " + jwtProvider.createAccessToken(owner.getId());
    }

    @SafeVarargs
    private static List<ResultActions> runConcurrently(Callable<ResultActions>... tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.length);
        CountDownLatch ready = new CountDownLatch(tasks.length);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<ResultActions>> futureList = new ArrayList<>();
            for (Callable<ResultActions> task : tasks) {
                futureList.add(executor.submit(() -> {
                    ready.countDown();
                    go.await();
                    return task.call();
                }));
            }
            ready.await();
            go.countDown();

            List<ResultActions> resultList = new ArrayList<>();
            for (Future<ResultActions> future : futureList) {
                resultList.add(future.get());
            }
            return resultList;
        } finally {
            executor.shutdownNow();
        }
    }

    private static void expectError(ResultActions result, ErrorCode errorCode) throws Exception {
        result.andExpect(status().is(errorCode.getStatus().value()))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.code").value(errorCode.name()));
    }
}
