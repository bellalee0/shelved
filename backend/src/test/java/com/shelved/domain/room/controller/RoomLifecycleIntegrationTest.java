package com.shelved.domain.room.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shelved.IntegrationTestSupport;
import com.shelved.common.entity.Accusation;
import com.shelved.common.entity.MysteryCase;
import com.shelved.common.entity.PlayResult;
import com.shelved.common.entity.Room;
import com.shelved.common.entity.RoomParticipant;
import com.shelved.common.entity.Suspect;
import com.shelved.common.entity.User;
import com.shelved.common.enums.RoomStatus;
import com.shelved.common.enums.UserRole;
import com.shelved.common.exception.CustomException;
import com.shelved.common.exception.ErrorCode;
import com.shelved.common.jwt.AuthUser;
import com.shelved.common.jwt.JwtProvider;
import com.shelved.domain.room.service.RoomLifecycleService;
import com.shelved.domain.room.service.RoomPlayService;
import com.shelved.fixture.ClocktowerCaseFixture;
import com.shelved.fixture.ClocktowerCaseFixture.SampleCase;
import jakarta.persistence.EntityManager;
import java.time.Duration;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 세션 시간 라이프사이클 (start·heartbeat·pause·resume·state), 플레이 요청 공통 검사, 동시 요청, 이탈 감지.
 * 시간은 MutableTestClock으로 고정·이동한다 (제한시간 1200초, 이탈 기준 15초)
 */
class RoomLifecycleIntegrationTest extends IntegrationTestSupport {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 5, 14, 0);
    private static final int LIMIT_SEC = 1200;
    private static final long LIMIT_MS = LIMIT_SEC * 1000L;
    private static final String CONNECTION_HEADER = "X-Connection-Id";
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoomLifecycleService roomLifecycleService;

    @Autowired
    private RoomPlayService roomPlayService;

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

    // --- POST start ---

    @Test
    void startRoom_READY_IN_PROGRESS와연결발급() throws Exception {

        // given
        Long roomId = saveReadyRoom(user);

        // when
        ResultActions result = mockMvc.perform(roomPost(roomId, "start"));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("수사 시작 성공"))
                .andExpect(jsonPath("$.data.heartbeatIntervalMs").value(5000))
                .andExpect(jsonPath("$.data.timer.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.timer.elapsedActiveMs").value(0))
                .andExpect(jsonPath("$.data.timer.remainingMs").value(LIMIT_MS))
                .andExpect(jsonPath("$.data.timer.awaitingGrading").value(false));
        String connectionId = readData(result).get("connectionId").asString();
        assertThat(connectionId).isNotBlank();
        Room room = findRoom(roomId);
        assertThat(room.getStatus()).isEqualTo(RoomStatus.IN_PROGRESS);
        assertThat(room.getStartedAt()).isEqualTo(T0);
        assertThat(room.getActiveSince()).isEqualTo(T0);
        assertThat(room.getLastSeenAt()).isEqualTo(T0);
        assertThat(room.getConnectionId()).isEqualTo(connectionId);
    }

    @Test
    void startRoom_READY아님_ROOM_NOT_READY() throws Exception {

        // given
        Long roomId = saveReadyRoom(user);
        start(roomId);

        // when
        ResultActions result = mockMvc.perform(roomPost(roomId, "start"));

        // then
        expectError(result, ErrorCode.ROOM_NOT_READY);
    }

    // --- POST heartbeat ---

    @Test
    void recordHeartbeat_현재연결_lastSeen갱신과시간반환() throws Exception {

        // given
        Long roomId = saveReadyRoom(user);
        String connectionId = start(roomId);
        testClock.advance(Duration.ofSeconds(5));

        // when
        ResultActions result = mockMvc.perform(roomPost(roomId, "heartbeat").header(CONNECTION_HEADER, connectionId));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("접속 확인 성공"))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.elapsedActiveMs").value(5000))
                .andExpect(jsonPath("$.data.remainingMs").value(LIMIT_MS - 5000));
        assertThat(findRoom(roomId).getLastSeenAt()).isEqualTo(T0.plusSeconds(5));
    }

    @Test
    void recordHeartbeat_헤더없음_상태와무관하게CONNECTION_ID_REQUIRED() throws Exception {

        // given
        Long inProgressRoomId = saveReadyRoom(user);
        start(inProgressRoomId);
        Long readyRoomId = saveReadyRoom(saveUser());

        // when
        ResultActions inProgressResult = mockMvc.perform(roomPost(inProgressRoomId, "heartbeat"));
        ResultActions readyResult = mockMvc.perform(post("/api/rooms/{roomId}/heartbeat", readyRoomId)
                .header(HttpHeaders.AUTHORIZATION, bearer(findOwner(readyRoomId))));

        // then
        expectError(inProgressResult, ErrorCode.CONNECTION_ID_REQUIRED);
        expectError(readyResult, ErrorCode.CONNECTION_ID_REQUIRED);
    }

    @Test
    void recordHeartbeat_다른연결_CONNECTION_REPLACED와lastSeen유지() throws Exception {

        // given
        Long roomId = saveReadyRoom(user);
        start(roomId);
        testClock.advance(Duration.ofSeconds(5));

        // when
        ResultActions result = mockMvc.perform(roomPost(roomId, "heartbeat").header(CONNECTION_HEADER, "other-connection"));

        // then
        expectError(result, ErrorCode.CONNECTION_REPLACED);
        assertThat(findRoom(roomId).getLastSeenAt()).isEqualTo(T0);
    }

    @Test
    void recordHeartbeat_PAUSED에늦게도착_상태만반환하고재개안함() throws Exception {

        // given
        Long roomId = saveReadyRoom(user);
        String connectionId = start(roomId);
        testClock.advance(Duration.ofSeconds(10));
        mockMvc.perform(roomPost(roomId, "pause")).andExpect(status().isOk());
        testClock.advance(Duration.ofSeconds(1));

        // when: 무효화된 이전 연결의 하트비트
        ResultActions result = mockMvc.perform(roomPost(roomId, "heartbeat").header(CONNECTION_HEADER, connectionId));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAUSED"))
                .andExpect(jsonPath("$.data.elapsedActiveMs").value(10_000));
        Room room = findRoom(roomId);
        assertThat(room.getStatus()).isEqualTo(RoomStatus.PAUSED);
        assertThat(room.getActiveSince()).isNull();
        assertThat(room.getConnectionId()).isNull();
        assertThat(room.getLastSeenAt()).isEqualTo(T0);
    }

    @Test
    void recordHeartbeat_유예구간에서소진_EXPIRED와PlayResult() throws Exception {

        // given: 1195초 하트비트 후 1205초 하트비트
        Long roomId = saveReadyRoom(user);
        String connectionId = start(roomId);
        keepAliveUntil(roomId, T0.plusSeconds(LIMIT_SEC - 5));
        testClock.advance(Duration.ofSeconds(10));

        // when
        ResultActions result = heartbeat(roomId, connectionId);

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("EXPIRED"))
                .andExpect(jsonPath("$.data.elapsedActiveMs").value(LIMIT_MS))
                .andExpect(jsonPath("$.data.remainingMs").value(0));
        assertThat(findPlayResults(roomId)).singleElement().satisfies(playResult -> {
            assertThat(playResult.isCleared()).isFalse();
            assertThat(playResult.getDurationSec()).isEqualTo(LIMIT_SEC);
            assertThat(playResult.getHintUsedCount()).isZero();
            assertThat(playResult.getEndedAt()).isEqualTo(T0.plusSeconds(LIMIT_SEC + 5));
        });
    }

    // --- POST pause ---

    @Test
    void pauseRoom_중복호출_시간한번만누적() throws Exception {

        // given
        Long roomId = saveReadyRoom(user);
        start(roomId);
        testClock.advance(Duration.ofSeconds(10));

        // when
        ResultActions first = mockMvc.perform(roomPost(roomId, "pause"));
        testClock.advance(Duration.ofSeconds(30));
        ResultActions second = mockMvc.perform(roomPost(roomId, "pause"));

        // then
        first.andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("일시정지 성공"))
                .andExpect(jsonPath("$.data.status").value("PAUSED"))
                .andExpect(jsonPath("$.data.elapsedActiveMs").value(10_000));
        second.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAUSED"))
                .andExpect(jsonPath("$.data.elapsedActiveMs").value(10_000));
        assertThat(findRoom(roomId).getElapsedActiveMs()).isEqualTo(10_000L);
    }

    @Test
    void pauseRoom_READY_아무것도바꾸지않음() throws Exception {

        // given
        Long roomId = saveReadyRoom(user);

        // when
        ResultActions result = mockMvc.perform(roomPost(roomId, "pause"));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.remainingMs").value(LIMIT_MS));
        assertThat(findRoom(roomId).getStatus()).isEqualTo(RoomStatus.READY);
    }

    @Test
    void pauseRoom_이탈기준이상_lastSeen까지만누적() throws Exception {

        // given: 5초 하트비트 후 30초에 pause
        Long roomId = saveReadyRoom(user);
        String connectionId = start(roomId);
        testClock.advance(Duration.ofSeconds(5));
        heartbeat(roomId, connectionId).andExpect(status().isOk());
        testClock.advance(Duration.ofSeconds(25));

        // when
        ResultActions result = mockMvc.perform(roomPost(roomId, "pause"));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAUSED"))
                .andExpect(jsonPath("$.data.elapsedActiveMs").value(5_000));
    }

    // --- POST resume ---

    @Test
    void resumeRoom_성공_새연결발급과이전연결CONNECTION_REPLACED() throws Exception {

        // given
        Long roomId = saveReadyRoom(user);
        String oldConnectionId = start(roomId);
        testClock.advance(Duration.ofSeconds(10));
        mockMvc.perform(roomPost(roomId, "pause")).andExpect(status().isOk());
        testClock.advance(Duration.ofMinutes(5));

        // when
        ResultActions result = mockMvc.perform(roomPost(roomId, "resume"));
        String newConnectionId = readData(result).get("connectionId").asString();
        testClock.advance(Duration.ofSeconds(3));
        ResultActions oldHeartbeat = heartbeat(roomId, oldConnectionId);
        ResultActions newHeartbeat = heartbeat(roomId, newConnectionId);

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("수사 이어하기 성공"))
                .andExpect(jsonPath("$.data.timer.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.timer.elapsedActiveMs").value(10_000));
        assertThat(newConnectionId).isNotBlank().isNotEqualTo(oldConnectionId);
        expectError(oldHeartbeat, ErrorCode.CONNECTION_REPLACED);
        newHeartbeat.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.elapsedActiveMs").value(13_000));
    }

    @Test
    void resumeRoom_PAUSED아님_ROOM_NOT_PAUSED() throws Exception {

        // given
        Long readyRoomId = saveReadyRoom(user);
        User otherUser = saveUser();
        Long inProgressRoomId = saveReadyRoom(otherUser);
        startAs(otherUser, inProgressRoomId);

        // when
        ResultActions readyResult = mockMvc.perform(roomPost(readyRoomId, "resume"));
        ResultActions inProgressResult = mockMvc.perform(post("/api/rooms/{roomId}/resume", inProgressRoomId)
                .header(HttpHeaders.AUTHORIZATION, bearer(findOwner(inProgressRoomId))));

        // then
        expectError(readyResult, ErrorCode.ROOM_NOT_PAUSED);
        expectError(inProgressResult, ErrorCode.ROOM_NOT_PAUSED);
    }

    @Test
    void resumeRoom_시간소진채점대기_ROOM_TIME_EXPIRED와상태의awaitingGrading() throws Exception {

        // given: 채점 대기(PENDING) 지목이 있는 상태에서 유예 구간 소진 후 pause → 보류된 PAUSED
        Long roomId = saveReadyRoom(user);
        start(roomId);
        keepAliveUntil(roomId, T0.plusSeconds(LIMIT_SEC - 5));
        savePendingAccusation(roomId);
        testClock.advance(Duration.ofSeconds(10));
        mockMvc.perform(roomPost(roomId, "pause"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAUSED"))
                .andExpect(jsonPath("$.data.awaitingGrading").value(true));

        // when
        ResultActions resumeResult = mockMvc.perform(roomPost(roomId, "resume"));
        ResultActions stateResult = mockMvc.perform(roomGet(roomId, "state"));

        // then
        expectError(resumeResult, ErrorCode.ROOM_TIME_EXPIRED);
        stateResult.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.timer.status").value("PAUSED"))
                .andExpect(jsonPath("$.data.timer.remainingMs").value(0))
                .andExpect(jsonPath("$.data.timer.elapsedActiveMs").value(LIMIT_MS))
                .andExpect(jsonPath("$.data.timer.awaitingGrading").value(true))
                .andExpect(jsonPath("$.data.accusationCount.used").value(0))
                .andExpect(jsonPath("$.data.accusationCount.reserved").value(1))
                .andExpect(jsonPath("$.data.accusationCount.remaining").value(2));
        assertThat(findPlayResults(roomId)).isEmpty();
    }

    // --- GET state ---

    @Test
    void getRoomState_READY_모든필드포함() throws Exception {

        // given
        Long roomId = saveReadyRoom(user);

        // when
        ResultActions result = mockMvc.perform(roomGet(roomId, "state"));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("세션 상태 조회 성공"))
                .andExpect(jsonPath("$.data.roomId").value(roomId))
                .andExpect(jsonPath("$.data.caseId").value(sample.mysteryCase().getId()))
                .andExpect(jsonPath("$.data.displayTitle").value(ClocktowerCaseFixture.DISPLAY_TITLE))
                .andExpect(jsonPath("$.data.difficulty").value("EASY"))
                .andExpect(jsonPath("$.data.isTest").value(false))
                .andExpect(jsonPath("$.data.timer.status").value("READY"))
                .andExpect(jsonPath("$.data.timer.timeLimitSec").value(LIMIT_SEC))
                .andExpect(jsonPath("$.data.timer.remainingMs").value(LIMIT_MS))
                .andExpect(jsonPath("$.data.heartbeatIntervalMs").value(5000))
                .andExpect(jsonPath("$.data.hintCount.limit").value(5))
                .andExpect(jsonPath("$.data.hintCount.used").value(0))
                .andExpect(jsonPath("$.data.hintCount.reserved").value(0))
                .andExpect(jsonPath("$.data.hintCount.remaining").value(5))
                .andExpect(jsonPath("$.data.accusationCount.limit").value(3))
                .andExpect(jsonPath("$.data.accusationCount.remaining").value(3));
        JsonNode data = readData(result);
        assertThat(data.has("startedAt")).isTrue();
        assertThat(data.get("startedAt").isNull()).isTrue();
        assertThat(data.has("pendingAccusation")).isTrue();
        assertThat(data.get("pendingAccusation").isNull()).isTrue();
        assertThat(data.get("aiRequests").isArray()).isTrue();
        assertThat(data.get("aiRequests").isEmpty()).isTrue();
        assertThat(data.get("conversations").isArray()).isTrue();
        assertThat(data.get("conversations").isEmpty()).isTrue();
    }

    @Test
    void getRoomState_다른사용자세션_ROOM_NOT_FOUND() throws Exception {

        // given
        Long roomId = saveReadyRoom(user);
        User other = saveUser();

        // when
        ResultActions result = mockMvc.perform(get("/api/rooms/{roomId}/state", roomId)
                .header(HttpHeaders.AUTHORIZATION, bearer(other)));

        // then
        expectError(result, ErrorCode.ROOM_NOT_FOUND);
    }

    @Test
    void getRoomState_하트비트끊김_lastSeen기준PAUSED로정산() throws Exception {

        // given
        Long roomId = saveReadyRoom(user);
        String connectionId = start(roomId);
        testClock.advance(Duration.ofSeconds(5));
        heartbeat(roomId, connectionId).andExpect(status().isOk());
        testClock.advance(Duration.ofSeconds(20));

        // when
        ResultActions result = mockMvc.perform(roomGet(roomId, "state"));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.startedAt").value("2026-11-05T14:00:00"))
                .andExpect(jsonPath("$.data.timer.status").value("PAUSED"))
                .andExpect(jsonPath("$.data.timer.elapsedActiveMs").value(5_000));
        Room room = findRoom(roomId);
        assertThat(room.getStatus()).isEqualTo(RoomStatus.PAUSED);
        assertThat(room.getConnectionId()).isNull();
    }

    @Test
    void getRoomState_유예구간소진_만료보류() throws Exception {

        // given
        Long roomId = saveReadyRoom(user);
        start(roomId);
        keepAliveUntil(roomId, T0.plusSeconds(LIMIT_SEC - 5));
        testClock.advance(Duration.ofSeconds(10));

        // when
        ResultActions result = mockMvc.perform(roomGet(roomId, "state"));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.timer.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.timer.remainingMs").value(0));
        assertThat(findRoom(roomId).getStatus()).isEqualTo(RoomStatus.IN_PROGRESS);
        assertThat(findPlayResults(roomId)).isEmpty();
    }

    // --- 플레이 요청 공통 검사 (RoomPlayService.acquirePlayableRoom) ---

    @Test
    void acquirePlayableRoom_헤더없음_상태검사보다먼저CONNECTION_ID_REQUIRED() {

        // given: PAUSED 세션
        Long roomId = saveReadyRoom(user);
        start(roomId);
        roomLifecycleService.pauseRoom(authUserOf(user), roomId);

        // when & then
        assertErrorCode(() -> acquire(roomId, null), ErrorCode.CONNECTION_ID_REQUIRED);
        assertErrorCode(() -> acquire(roomId, " "), ErrorCode.CONNECTION_ID_REQUIRED);
    }

    @Test
    void acquirePlayableRoom_진행중아님_ROOM_NOT_IN_PROGRESS() {

        // given
        Long readyRoomId = saveReadyRoom(user);

        // when & then
        assertErrorCode(() -> acquire(readyRoomId, "any"), ErrorCode.ROOM_NOT_IN_PROGRESS);
    }

    @Test
    void acquirePlayableRoom_이탈확정후거부_PAUSED정산은커밋() {

        // given: 5초 하트비트 후 25초에 플레이 요청
        Long roomId = saveReadyRoom(user);
        String connectionId = start(roomId);
        testClock.advance(Duration.ofSeconds(5));
        roomLifecycleService.recordHeartbeat(authUserOf(user), roomId, connectionId);
        testClock.advance(Duration.ofSeconds(20));

        // when & then
        assertErrorCode(() -> acquire(roomId, connectionId), ErrorCode.ROOM_NOT_IN_PROGRESS);
        Room room = findRoom(roomId);
        assertThat(room.getStatus()).isEqualTo(RoomStatus.PAUSED);
        assertThat(room.getElapsedActiveMs()).isEqualTo(5_000L);
    }

    @Test
    void acquirePlayableRoom_시간소진_연결검사보다먼저ROOM_TIME_EXPIRED와만료커밋() {

        // given: 유예 구간 소진 + 다른 연결
        Long roomId = saveReadyRoom(user);
        start(roomId);
        keepAliveUntil(roomId, T0.plusSeconds(LIMIT_SEC - 5));
        testClock.advance(Duration.ofSeconds(10));

        // when & then
        assertErrorCode(() -> acquire(roomId, "other-connection"), ErrorCode.ROOM_TIME_EXPIRED);
        Room room = findRoom(roomId);
        assertThat(room.getStatus()).isEqualTo(RoomStatus.EXPIRED);
        assertThat(room.getElapsedActiveMs()).isEqualTo(LIMIT_MS);
        assertThat(findPlayResults(roomId)).hasSize(1);
    }

    @Test
    void acquirePlayableRoom_다른연결_CONNECTION_REPLACED() {

        // given
        Long roomId = saveReadyRoom(user);
        start(roomId);

        // when & then
        assertErrorCode(() -> acquire(roomId, "other-connection"), ErrorCode.CONNECTION_REPLACED);
    }

    @Test
    void acquirePlayableRoom_다른사용자세션_ROOM_NOT_FOUND() {

        // given
        Long roomId = saveReadyRoom(user);
        String connectionId = start(roomId);
        User other = saveUser();

        // when & then
        assertErrorCode(() -> transactionTemplate.execute(status ->
                roomPlayService.acquirePlayableRoom(roomId, authUserOf(other), connectionId)), ErrorCode.ROOM_NOT_FOUND);
    }

    @Test
    void acquirePlayableRoom_성공_잠긴세션반환과lastSeen갱신() {

        // given
        Long roomId = saveReadyRoom(user);
        String connectionId = start(roomId);
        testClock.advance(Duration.ofSeconds(8));

        // when
        Room acquired = acquire(roomId, connectionId);

        // then
        assertThat(acquired.getId()).isEqualTo(roomId);
        Room room = findRoom(roomId);
        assertThat(room.getStatus()).isEqualTo(RoomStatus.IN_PROGRESS);
        assertThat(room.getLastSeenAt()).isEqualTo(T0.plusSeconds(8));
    }

    // --- 동시 요청 ---

    @Test
    void pauseRoomAndRecordHeartbeat_동시요청_시간한번만누적하고PAUSED유지() throws Exception {

        // given
        Long roomId = saveReadyRoom(user);
        String connectionId = start(roomId);
        testClock.advance(Duration.ofSeconds(10));

        // when
        List<ResultActions> resultList = runConcurrently(
                () -> mockMvc.perform(roomPost(roomId, "pause")),
                () -> heartbeat(roomId, connectionId));

        // then
        for (ResultActions result : resultList) {
            result.andExpect(status().isOk());
        }
        Room room = findRoom(roomId);
        assertThat(room.getStatus()).isEqualTo(RoomStatus.PAUSED);
        assertThat(room.getElapsedActiveMs()).isEqualTo(10_000L);
        assertThat(room.getActiveSince()).isNull();
        assertThat(room.getConnectionId()).isNull();
    }

    @Test
    void pauseRoom_동시2건_시간한번만누적() throws Exception {

        // given
        Long roomId = saveReadyRoom(user);
        start(roomId);
        testClock.advance(Duration.ofSeconds(10));

        // when
        List<ResultActions> resultList = runConcurrently(
                () -> mockMvc.perform(roomPost(roomId, "pause")),
                () -> mockMvc.perform(roomPost(roomId, "pause")));

        // then
        for (ResultActions result : resultList) {
            result.andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("PAUSED"))
                    .andExpect(jsonPath("$.data.elapsedActiveMs").value(10_000));
        }
        assertThat(findRoom(roomId).getElapsedActiveMs()).isEqualTo(10_000L);
    }

    @Test
    void 만료동시발생_PlayResult1건() throws Exception {

        // given: 유예 구간에서 소진된 상태로 하트비트·pause·하트비트가 동시에 도착
        Long roomId = saveReadyRoom(user);
        String connectionId = start(roomId);
        keepAliveUntil(roomId, T0.plusSeconds(LIMIT_SEC - 5));
        testClock.advance(Duration.ofSeconds(10));

        // when
        List<ResultActions> resultList = runConcurrently(
                () -> heartbeat(roomId, connectionId),
                () -> mockMvc.perform(roomPost(roomId, "pause")),
                () -> heartbeat(roomId, connectionId));

        // then
        for (ResultActions result : resultList) {
            result.andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("EXPIRED"));
        }
        assertThat(findRoom(roomId).getStatus()).isEqualTo(RoomStatus.EXPIRED);
        assertThat(findPlayResults(roomId)).hasSize(1);
    }

    // --- 이탈 감지 ---

    @Test
    void pauseDisconnectedRooms_15초미수신만PAUSED_유예구간은유지() {

        // given: A는 5초, B는 10초에 마지막 하트비트. 20초에 점검 → A 15초 미수신, B 10초 미수신
        Long disconnectedRoomId = saveReadyRoom(user);
        String disconnectedConnection = start(disconnectedRoomId);
        User otherUser = saveUser();
        Long aliveRoomId = saveReadyRoom(otherUser);
        String aliveConnection = startAs(otherUser, aliveRoomId);
        testClock.advance(Duration.ofSeconds(5));
        roomLifecycleService.recordHeartbeat(authUserOf(user), disconnectedRoomId, disconnectedConnection);
        testClock.advance(Duration.ofSeconds(5));
        roomLifecycleService.recordHeartbeat(authUserOf(otherUser), aliveRoomId, aliveConnection);
        testClock.advance(Duration.ofSeconds(10));

        // when
        roomLifecycleService.pauseDisconnectedRooms();

        // then
        Room disconnected = findRoom(disconnectedRoomId);
        assertThat(disconnected.getStatus()).isEqualTo(RoomStatus.PAUSED);
        assertThat(disconnected.getElapsedActiveMs()).isEqualTo(5_000L);
        assertThat(disconnected.getConnectionId()).isNull();
        Room alive = findRoom(aliveRoomId);
        assertThat(alive.getStatus()).isEqualTo(RoomStatus.IN_PROGRESS);
        assertThat(alive.getConnectionId()).isEqualTo(aliveConnection);
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

    private void savePendingAccusation(Long roomId) {
        transactionTemplate.executeWithoutResult(status -> entityManager.persist(new Accusation(
                entityManager.getReference(Room.class, roomId),
                entityManager.getReference(User.class, user.getId()),
                entityManager.getReference(Suspect.class, sample.butler().getId()),
                UUID.randomUUID().toString(), "가상 답안", LocalDateTime.now(testClock), LIMIT_MS - 1_000L)));
    }

    private String start(Long roomId) {
        return startAs(user, roomId);
    }

    private String startAs(User owner, Long roomId) {
        return roomLifecycleService.startRoom(authUserOf(owner), roomId).connectionId();
    }

    private Room acquire(Long roomId, String connectionId) {
        return transactionTemplate.execute(status -> roomPlayService.acquirePlayableRoom(roomId, authUserOf(user), connectionId));
    }

    private ResultActions heartbeat(Long roomId, String connectionId) throws Exception {
        return mockMvc.perform(roomPost(roomId, "heartbeat").header(CONNECTION_HEADER, connectionId));
    }

    private MockHttpServletRequestBuilder roomPost(Long roomId, String action) {
        return post("/api/rooms/{roomId}/" + action, roomId).header(HttpHeaders.AUTHORIZATION, bearer(user));
    }

    private MockHttpServletRequestBuilder roomGet(Long roomId, String resource) {
        return get("/api/rooms/{roomId}/" + resource, roomId).header(HttpHeaders.AUTHORIZATION, bearer(user));
    }

    private Room findRoom(Long roomId) {
        return transactionTemplate.execute(status -> entityManager.find(Room.class, roomId));
    }

    private User findOwner(Long roomId) {
        return transactionTemplate.execute(status -> {
            Long hostUserId = entityManager.find(Room.class, roomId).getHostUser().getId();
            return entityManager.find(User.class, hostUserId);
        });
    }

    private List<PlayResult> findPlayResults(Long roomId) {
        return transactionTemplate.execute(status -> entityManager.createQuery(
                        "select p from PlayResult p where p.room.id = :roomId", PlayResult.class)
                .setParameter("roomId", roomId)
                .getResultList());
    }

    private static AuthUser authUserOf(User owner) {
        return new AuthUser(owner.getId(), owner.getRole(), false);
    }

    // 토큰은 요청마다 현재 테스트 시각으로 발급한다 (시각을 크게 옮겨도 만료되지 않도록)
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

    private static JsonNode readData(ResultActions result) throws Exception {
        return JSON_MAPPER.readTree(result.andReturn().getResponse().getContentAsString()).get("data");
    }

    private static void assertErrorCode(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getErrorCode()).isEqualTo(errorCode));
    }

    private static void expectError(ResultActions result, ErrorCode errorCode) throws Exception {
        result.andExpect(status().is(errorCode.getStatus().value()))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.code").value(errorCode.name()));
    }
}
