package com.shelved.domain.airequest.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shelved.common.entity.AiRequest;
import com.shelved.common.entity.Room;
import com.shelved.common.entity.User;
import com.shelved.common.entity.json.AiRequestInput;
import com.shelved.common.enums.AiRequestType;
import com.shelved.common.enums.RoomStatus;
import com.shelved.common.exception.ErrorCode;
import com.shelved.domain.airequest.AiRequestIntegrationTestSupport;
import com.shelved.domain.airequest.service.AiRequestCommand;
import com.shelved.domain.airequest.service.AiRequestExecutor;
import com.shelved.domain.airequest.service.TestAiRequestHandler;
import com.shelved.domain.room.repository.AiRequestRepository;
import com.shelved.infra.llm.FakeLlmScenario;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * GET /api/rooms/{roomId}/ai-requests/{requestId}와 GET /api/rooms/{roomId}/state의 aiRequests
 */
class AiRequestStatusIntegrationTest extends AiRequestIntegrationTestSupport {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final String QUESTION = "어젯밤 9시에 서재 근처에 있었나요?";
    private static final Set<String> STATUS_FIELDS = Set.of(
            "requestId", "type", "targetId", "status", "input", "result", "error", "retryAvailableAt", "acceptedAt", "updatedAt");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AiRequestExecutor aiRequestExecutor;

    @Autowired
    private AiRequestRepository aiRequestRepository;

    private Long roomId;
    private String connectionId;

    @BeforeEach
    void setUp() {
        roomId = saveReadyRoom(user);
        connectionId = start(user, roomId);
    }

    // --- GET ai-requests/{requestId} ---

    @Test
    void getAiRequest_본인세션성공요청_스냅샷반환_내부필드없음_연결헤더불필요() throws Exception {

        // given
        fakeLlmClient.enqueue(FakeLlmScenario.respond("답변: 9시에는 부엌에 있었습니다."));
        String requestId = newRequestId();
        aiRequestExecutor.execute(authUserOf(user), new AiRequestCommand(roomId, requestId, sample.butler().getId(),
                AiRequestInput.question(QUESTION), connectionId), npcHandler());

        // when
        ResultActions result = mockMvc.perform(getAiRequest(user, roomId, requestId));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("AI 요청 상태 조회 성공"))
                .andExpect(jsonPath("$.data.requestId").value(requestId))
                .andExpect(jsonPath("$.data.type").value("NPC_ASK"))
                .andExpect(jsonPath("$.data.targetId").value(sample.butler().getId()))
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.input.question").value(QUESTION))
                .andExpect(jsonPath("$.data.result.answer").value("9시에는 부엌에 있었습니다."))
                .andExpect(jsonPath("$.data.result.usedCount").value(1))
                .andExpect(jsonPath("$.data.error").doesNotExist())
                .andExpect(jsonPath("$.data.retryAvailableAt").doesNotExist())
                .andExpect(jsonPath("$.data.acceptedAt").value("2026-11-12T14:00:00"));
        JsonNode data = readData(result);
        assertThat(fieldNames(data)).isEqualTo(STATUS_FIELDS);
        assertThat(fieldNames(data.get("input"))).containsExactly("question");
        String body = result.andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(TestAiRequestHandler.SYSTEM_PROMPT, "executionNo", "inputJson", "resultRefId");
    }

    @Test
    void getAiRequest_다른사용자세션_ROOM_NOT_FOUND() throws Exception {

        // given
        String requestId = saveAiRequest(roomId, AiRequestType.HINT, null, T0, null);
        User other = saveUser();

        // when
        ResultActions result = mockMvc.perform(getAiRequest(other, roomId, requestId));

        // then
        expectError(result, ErrorCode.ROOM_NOT_FOUND);
    }

    @Test
    void getAiRequest_없는요청_AI_REQUEST_NOT_FOUND() throws Exception {

        // given
        Long otherRoomId = saveReadyRoom(user);
        String otherRoomRequestId = saveAiRequest(otherRoomId, AiRequestType.HINT, null, T0, null);

        // when: 없는 ID, 다른 세션의 ID
        ResultActions missing = mockMvc.perform(getAiRequest(user, roomId, newRequestId()));
        ResultActions otherRoom = mockMvc.perform(getAiRequest(user, roomId, otherRoomRequestId));

        // then
        expectError(missing, ErrorCode.AI_REQUEST_NOT_FOUND);
        expectError(otherRoom, ErrorCode.AI_REQUEST_NOT_FOUND);
    }

    @Test
    void getAiRequest_일시정지와종료후에도조회() throws Exception {

        // given
        String requestId = saveAiRequest(roomId, AiRequestType.NPC_ASK, sample.butler().getId(), T0, ErrorCode.NPC_ANSWER_FAILED);
        roomLifecycleService.pauseRoom(authUserOf(user), roomId);

        // when
        ResultActions paused = mockMvc.perform(getAiRequest(user, roomId, requestId));
        updateRoomStatus(roomId, RoomStatus.EXPIRED);
        ResultActions ended = mockMvc.perform(getAiRequest(user, roomId, requestId));

        // then
        for (ResultActions result : List.of(paused, ended)) {
            result.andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("FAILED"))
                    .andExpect(jsonPath("$.data.error.code").value("NPC_ANSWER_FAILED"))
                    .andExpect(jsonPath("$.data.error.message").value(ErrorCode.NPC_ANSWER_FAILED.getMessage()))
                    .andExpect(jsonPath("$.data.result").doesNotExist());
        }
    }

    // --- GET state aiRequests ---

    @Test
    void getRoomState_aiRequests_처리중과대상별재시도대기실패만() throws Exception {

        // given
        Long butlerId = sample.butler().getId();
        Long housekeeperId = sample.housekeeper().getId();
        Long gardenerId = sample.gardener().getId();
        // 집사: 실패 뒤 같은 대상에 새 요청이 성공 → 둘 다 제외
        saveAiRequest(roomId, AiRequestType.NPC_ASK, butlerId, T0, ErrorCode.NPC_ANSWER_FAILED);
        saveSucceeded(roomId, AiRequestType.NPC_ASK, butlerId, T0.plusSeconds(10));
        // 가정부: 마지막 요청이 실패 → 포함
        String housekeeperFailed = saveAiRequest(roomId, AiRequestType.NPC_ASK, housekeeperId, T0.plusSeconds(20), ErrorCode.NPC_ANSWER_FAILED);
        // 정원사: 처리 중 → 포함
        String gardenerProcessing = saveAiRequest(roomId, AiRequestType.NPC_ASK, gardenerId, T0.plusSeconds(30), null);
        // 힌트: 성공 → 제외
        saveSucceeded(roomId, AiRequestType.HINT, null, T0.plusSeconds(40));
        // 지목: 채점 실패(14:01:00) → 포함, 재채점 가능 시각 = 실패 시각 + 10초
        String accusationFailed = saveAiRequest(roomId, AiRequestType.ACCUSATION, null, T0.plusSeconds(50), ErrorCode.GRADING_FAILED);

        // when
        ResultActions result = mockMvc.perform(get("/api/rooms/{roomId}/state", roomId)
                .header(HttpHeaders.AUTHORIZATION, bearer(user)));

        // then
        result.andExpect(status().isOk());
        JsonNode aiRequests = readData(result).get("aiRequests");
        List<String> requestIdList = new ArrayList<>();
        aiRequests.forEach(node -> requestIdList.add(node.get("requestId").asString()));
        assertThat(requestIdList).containsExactly(housekeeperFailed, gardenerProcessing, accusationFailed);
        aiRequests.forEach(node -> assertThat(fieldNames(node)).isEqualTo(STATUS_FIELDS));
        assertThat(aiRequests.get(0).get("error").get("code").asString()).isEqualTo("NPC_ANSWER_FAILED");
        assertThat(aiRequests.get(1).get("status").asString()).isEqualTo("PROCESSING");
        assertThat(aiRequests.get(2).get("retryAvailableAt").asString()).isEqualTo("2026-11-12T14:01:10");
    }

    // --- helpers ---

    private TestAiRequestHandler npcHandler() {
        return new TestAiRequestHandler(AiRequestType.NPC_ASK, aiRequestRepository, 5);
    }

    /**
     * failureCode가 null이면 PROCESSING, 아니면 그 코드로 실패한 요청 (실패 시각 = 접수 + 10초)
     */
    private String saveAiRequest(Long targetRoomId, AiRequestType type, Long targetId, LocalDateTime acceptedAt, ErrorCode failureCode) {
        String requestId = newRequestId();
        transactionTemplate.executeWithoutResult(status -> {
            AiRequest aiRequest = new AiRequest(entityManager.getReference(Room.class, targetRoomId),
                    requestId, type, targetId, AiRequestInput.question(QUESTION), acceptedAt);
            if (failureCode != null) {
                aiRequest.fail(failureCode, acceptedAt.plusSeconds(10));
            }
            entityManager.persist(aiRequest);
        });
        return requestId;
    }

    private void saveSucceeded(Long targetRoomId, AiRequestType type, Long targetId, LocalDateTime acceptedAt) {
        transactionTemplate.executeWithoutResult(status -> {
            AiRequest aiRequest = new AiRequest(entityManager.getReference(Room.class, targetRoomId),
                    newRequestId(), type, targetId, AiRequestInput.question(QUESTION), acceptedAt);
            aiRequest.succeed(null, JSON_MAPPER.readTree("{\"answer\":\"가상 답\"}"), acceptedAt.plusSeconds(5));
            entityManager.persist(aiRequest);
        });
    }

    private void updateRoomStatus(Long targetRoomId, RoomStatus roomStatus) {
        transactionTemplate.executeWithoutResult(status -> entityManager.createQuery(
                        "update Room r set r.status = :status where r.id = :roomId")
                .setParameter("status", roomStatus)
                .setParameter("roomId", targetRoomId)
                .executeUpdate());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder getAiRequest(
            User owner, Long targetRoomId, String requestId) {
        return get("/api/rooms/{roomId}/ai-requests/{requestId}", targetRoomId, requestId)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner));
    }

    private static Set<String> fieldNames(JsonNode node) {
        return Set.copyOf(node.propertyNames());
    }

    private static JsonNode readData(ResultActions result) throws Exception {
        return JSON_MAPPER.readTree(result.andReturn().getResponse().getContentAsString()).get("data");
    }

    private static void expectError(ResultActions result, ErrorCode errorCode) throws Exception {
        result.andExpect(status().is(errorCode.getStatus().value()))
                .andExpect(jsonPath("$.data.code").value(errorCode.name()))
                .andExpect(jsonPath("$.message").value(errorCode.getMessage()));
    }
}
