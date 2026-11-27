package com.shelved.domain.accusation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.shelved.common.entity.Accusation;
import com.shelved.common.entity.PlayResult;
import com.shelved.common.entity.Room;
import com.shelved.common.entity.Suspect;
import com.shelved.common.entity.User;
import com.shelved.common.enums.Verdict;
import com.shelved.domain.hint.HintIntegrationTestSupport;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 지목 통합 테스트 공통 준비. 세션 준비·응답 읽기·동시 실행 도우미는 HintIntegrationTestSupport의 것을 그대로 쓴다.
 * 가상 사건 「시계탑 저택의 마지막 종소리」: 범인은 집사(butler), 핵심 요소 1개에 필수 항목 trk-1-i1·trk-1-i2.
 * Fake LLM: enqueue한 시나리오가 없으면 채점 기본 응답(모든 항목 MET)이다
 */
public abstract class AccusationIntegrationTestSupport extends HintIntegrationTestSupport {

    protected static final String TRICK = "집사가 녹음기로 피해자의 목소리를 틀어 살아 있는 시각을 속였다.";
    protected static final String FEEDBACK = "녹음기를 짚은 것은 맞아요. 어떤 목적으로 사용했을까요?";
    protected static final long LIMIT_MS = LIMIT_SEC * 1000L;

    // --- 요청 ---

    protected MockHttpServletRequestBuilder accusationRequest(
            User owner,
            Long targetRoomId,
            String connection,
            String requestId,
            Long suspectId,
            String trickExplanation
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("requestId", requestId);
        body.put("suspectId", suspectId);
        body.put("trickExplanation", trickExplanation);
        MockHttpServletRequestBuilder builder = post("/api/rooms/{roomId}/accusations", targetRoomId)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON_MAPPER.writeValueAsString(body));
        if (connection != null) {
            builder.header(CONNECTION_ID_HEADER, connection);
        }
        return builder;
    }

    /**
     * 이 테스트의 세션에 집사를 지목하는 새 답안 (현재 연결)
     */
    protected MockHttpServletRequestBuilder accuseButler(String requestId) {
        return accusationRequest(user, roomId, connectionId, requestId, sample.butler().getId(), TRICK);
    }

    protected MockHttpServletRequestBuilder accusationListRequest(User owner, Long targetRoomId) {
        return get("/api/rooms/{roomId}/accusations", targetRoomId)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner));
    }

    protected MockHttpServletRequestBuilder aiRequestStatusRequest(String requestId) {
        return get("/api/rooms/{roomId}/ai-requests/{requestId}", roomId, requestId)
                .header(HttpHeaders.AUTHORIZATION, bearer(user));
    }

    // --- 채점 응답 (Fake LLM에 enqueue) ---

    /**
     * 항목 trk-1-i1·trk-1-i2의 판정을 순서대로 담은 채점 응답
     */
    protected String grading(String firstResult, String secondResult) {
        Long elementId = sample.trickElement().getId();
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(item(elementId, "trk-1-i1", firstResult));
        items.add(item(elementId, "trk-1-i2", secondResult));
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("items", items);
        root.put("feedback", FEEDBACK);
        return JSON_MAPPER.writeValueAsString(root);
    }

    protected String partialGrading() {
        return grading("MET", "MISSING");
    }

    protected String allMetGrading() {
        return grading("MET", "MET");
    }

    private static Map<String, Object> item(Long elementId, String itemId, String result) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("trickElementId", elementId);
        item.put("itemId", itemId);
        item.put("result", result);
        item.put("evidence", "MISSING".equals(result) ? "" : "녹음기를 틀었다");
        item.put("reason", "판단 이유");
        return item;
    }

    // --- 데이터 준비·조회 ---

    /**
     * 정상 채점 완료(COMPLETED, 부분 정답) 지목을 바로 저장한다
     */
    protected void saveCompletedAccusations(Long targetRoomId, int count) {
        transactionTemplate.executeWithoutResult(status -> {
            Room room = entityManager.find(Room.class, targetRoomId);
            for (int n = 1; n <= count; n++) {
                Accusation accusation = new Accusation(room, room.getHostUser(),
                        entityManager.getReference(Suspect.class, sample.butler().getId()),
                        newRequestId(), "이전 답안 " + n, T0.plusSeconds(n), n * 1000L);
                accusation.completeGrading(true, Verdict.PARTIAL, List.of(), "이전 피드백 " + n);
                entityManager.persist(accusation);
            }
        });
    }

    protected List<Accusation> findAccusationList(Long targetRoomId) {
        return transactionTemplate.execute(status -> entityManager.createQuery(
                        "select a from Accusation a where a.room.id = :roomId order by a.id", Accusation.class)
                .setParameter("roomId", targetRoomId)
                .getResultList());
    }

    protected Accusation findAccusation(Long targetRoomId, String requestId) {
        return findAccusationList(targetRoomId).stream()
                .filter(accusation -> accusation.getRequestId().equals(requestId))
                .findFirst()
                .orElseThrow();
    }

    protected List<PlayResult> findPlayResultList(Long targetRoomId) {
        return transactionTemplate.execute(status -> entityManager.createQuery(
                        "select p from PlayResult p where p.room.id = :roomId", PlayResult.class)
                .setParameter("roomId", targetRoomId)
                .getResultList());
    }

    /**
     * 세션 시작(T0)부터 진행 시간 elapsedMs가 지난 시각
     */
    protected static LocalDateTime atElapsed(long elapsedMs) {
        return T0.plusNanos(elapsedMs * 1_000_000L);
    }
}
