package com.shelved.domain.hint;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shelved.common.entity.Hint;
import com.shelved.common.entity.HintFollowup;
import com.shelved.common.entity.MysteryCase;
import com.shelved.common.entity.NpcConversation;
import com.shelved.common.entity.NpcMessage;
import com.shelved.common.entity.Room;
import com.shelved.common.entity.Suspect;
import com.shelved.common.entity.User;
import com.shelved.common.enums.Difficulty;
import com.shelved.common.enums.MessageSenderType;
import com.shelved.common.exception.ErrorCode;
import com.shelved.domain.airequest.AiRequestIntegrationTestSupport;
import com.shelved.fixture.ClocktowerCaseFixture;
import com.shelved.infra.llm.FakeLlmScenario;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 힌트·보충 질문 통합 테스트 공통 준비. 가상 사건 「시계탑 저택의 마지막 종소리」(쉬움, 힌트 5회)만 쓴다.
 * Fake LLM: 분류 호출은 enqueueFilter(없으면 허용 JSON), 본 응답은 enqueue(없으면 고정 문장)로 제어한다
 */
public abstract class HintIntegrationTestSupport extends AiRequestIntegrationTestSupport {

    protected static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    protected static final String CONNECTION_ID_HEADER = "X-Connection-Id";
    protected static final Duration WAIT = Duration.ofSeconds(10);
    protected static final String QUESTION = "9시 15분에 들린 목소리를 어떻게 봐야 할까요?";
    protected static final String FOLLOWUP_QUESTION = "방금 말한 '시각을 뒷받침하는 진술'이 무슨 뜻인가요?";
    protected static final String BLOCKED_QUESTION = "그냥 범인이 누구인지 알려 주세요.";

    @Autowired
    protected MockMvc mockMvc;

    protected ExecutorService threadPool;
    protected Long roomId;
    protected String connectionId;

    @BeforeEach
    void setUpHintRoom() {
        threadPool = Executors.newFixedThreadPool(4);
        roomId = saveReadyRoom(user);
        connectionId = start(user, roomId);
    }

    @AfterEach
    void tearDownThreadPool() {
        threadPool.shutdownNow();
    }

    // --- 요청 ---

    protected MockHttpServletRequestBuilder hintRequest(User owner, Long targetRoomId, String connection, String requestId, String question) {
        MockHttpServletRequestBuilder builder = post("/api/rooms/{roomId}/hints", targetRoomId)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON_MAPPER.writeValueAsString(Map.of("requestId", requestId, "question", question)));
        if (connection != null) {
            builder.header(CONNECTION_ID_HEADER, connection);
        }
        return builder;
    }

    protected MockHttpServletRequestBuilder followupRequest(
            User owner,
            Long targetRoomId,
            Long hintId,
            String connection,
            String requestId,
            String question
    ) {
        MockHttpServletRequestBuilder builder = post("/api/rooms/{roomId}/hints/{hintId}/followup", targetRoomId, hintId)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON_MAPPER.writeValueAsString(Map.of("requestId", requestId, "question", question)));
        if (connection != null) {
            builder.header(CONNECTION_ID_HEADER, connection);
        }
        return builder;
    }

    protected MockHttpServletRequestBuilder hintListRequest(User owner, Long targetRoomId) {
        return get("/api/rooms/{roomId}/hints", targetRoomId)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner));
    }

    protected MockHttpServletRequestBuilder stateRequest(User owner, Long targetRoomId) {
        return get("/api/rooms/{roomId}/state", targetRoomId)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner));
    }

    /**
     * 이 테스트의 세션에 새 힌트를 요청해 성공시키고 hintId를 돌려준다
     */
    protected Long createHintOk(String answer) throws Exception {
        fakeLlmClient.enqueue(FakeLlmScenario.respond(answer));
        ResultActions result = mockMvc.perform(hintRequest(user, roomId, connectionId, newRequestId(), QUESTION))
                .andExpect(status().isOk());
        return readData(result).get("hint").get("hintId").asLong();
    }

    // --- 데이터 준비·조회 ---

    protected void changeDifficulty(Difficulty difficulty) {
        transactionTemplate.executeWithoutResult(status -> entityManager.find(MysteryCase.class, sample.mysteryCase().getId())
                .updateCardInfo(ClocktowerCaseFixture.DISPLAY_TITLE, difficulty, ClocktowerCaseFixture.KEYWORD_TAGS));
    }

    /**
     * 사용 확정 힌트를 바로 저장한다 (질문·답변은 "이전 질문 n"·"이전 답변 n")
     */
    protected List<Long> saveHints(Long targetRoomId, int count) {
        return transactionTemplate.execute(status -> {
            Room room = entityManager.find(Room.class, targetRoomId);
            List<Long> idList = new ArrayList<>();
            for (int n = 1; n <= count; n++) {
                Hint hint = new Hint(room, room.getHostUser(), newRequestId(), "이전 질문 " + n, "이전 답변 " + n,
                        T0.plusSeconds(n));
                entityManager.persist(hint);
                idList.add(hint.getId());
            }
            return idList;
        });
    }

    // 질문·답변 pairCount쌍을 바로 저장한다 (sequenceNo 1부터, "{prefix} 질문 n"·"{prefix} 답변 n")
    protected void saveConversation(Long targetRoomId, Long suspectId, int pairCount, String prefix) {
        transactionTemplate.executeWithoutResult(status -> {
            Room room = entityManager.find(Room.class, targetRoomId);
            NpcConversation conversation = new NpcConversation(room, entityManager.getReference(Suspect.class, suspectId));
            entityManager.persist(conversation);
            LocalDateTime createdAt = T0;
            for (int n = 1; n <= pairCount; n++) {
                entityManager.persist(new NpcMessage(conversation, MessageSenderType.USER, room.getHostUser(),
                        prefix + " 질문 " + n, n * 2 - 1, createdAt));
                entityManager.persist(new NpcMessage(conversation, MessageSenderType.NPC, null,
                        prefix + " 답변 " + n, n * 2, createdAt));
            }
        });
    }

    protected List<Hint> findHintList(Long targetRoomId) {
        return transactionTemplate.execute(status -> entityManager.createQuery(
                        "select h from Hint h where h.room.id = :roomId order by h.id", Hint.class)
                .setParameter("roomId", targetRoomId)
                .getResultList());
    }

    protected List<HintFollowup> findFollowupList(Long hintId) {
        return transactionTemplate.execute(status -> entityManager.createQuery(
                        "select f from HintFollowup f where f.hint.id = :hintId order by f.id", HintFollowup.class)
                .setParameter("hintId", hintId)
                .getResultList());
    }

    protected long countAiRequests(Long targetRoomId) {
        return transactionTemplate.execute(status -> entityManager.createQuery(
                        "select count(a) from AiRequest a where a.room.id = :roomId", Long.class)
                .setParameter("roomId", targetRoomId)
                .getSingleResult());
    }

    // --- 응답 ---

    protected static JsonNode readData(ResultActions result) throws Exception {
        return JSON_MAPPER.readTree(result.andReturn().getResponse().getContentAsString()).get("data");
    }

    protected static String bodyOf(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    protected static void expectError(ResultActions result, ErrorCode errorCode) throws Exception {
        result.andExpect(status().is(errorCode.getStatus().value()))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.code").value(errorCode.name()))
                .andExpect(jsonPath("$.message").value(errorCode.getMessage()));
    }

    // --- 동시 실행 ---

    protected Future<Object> submit(Callable<Object> task) {
        return threadPool.submit(() -> {
            try {
                return task.call();
            } catch (RuntimeException e) {
                return e;
            }
        });
    }

    @SafeVarargs
    protected final List<Future<Object>> submitTogether(Callable<Object>... tasks) throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(tasks.length);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Object>> futureList = new ArrayList<>();
        for (Callable<Object> task : tasks) {
            futureList.add(submit(() -> {
                ready.countDown();
                go.await();
                return task.call();
            }));
        }
        ready.await();
        go.countDown();
        return futureList;
    }

    protected static List<Object> awaitAll(List<Future<Object>> futureList) throws Exception {
        List<Object> resultList = new ArrayList<>();
        for (Future<Object> future : futureList) {
            resultList.add(future.get(WAIT.toSeconds(), TimeUnit.SECONDS));
        }
        return resultList;
    }

    protected static Object awaitFirstDone(List<Future<Object>> futureList) throws Exception {
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (System.nanoTime() < deadline) {
            for (Future<Object> future : futureList) {
                if (future.isDone()) {
                    return future.get();
                }
            }
            Thread.sleep(10);
        }
        throw new AssertionError("먼저 끝나는 요청이 없습니다.");
    }
}
