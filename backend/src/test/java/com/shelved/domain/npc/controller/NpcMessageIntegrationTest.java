package com.shelved.domain.npc.controller;

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
import com.shelved.domain.npc.NpcIntegrationTestSupport;
import com.shelved.fixture.ClocktowerCaseFixture;
import com.shelved.infra.llm.FakeLlmScenario;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

/**
 * GET /api/rooms/{roomId}/npc/{suspectId}/messages 와 GET state의 conversations. 가상 사건 「시계탑 저택의 마지막 종소리」만 쓴다
 */
class NpcMessageIntegrationTest extends NpcIntegrationTestSupport {

    private Long roomId;
    private String connectionId;
    private Long butlerId;
    private Long housekeeperId;

    @BeforeEach
    void setUp() {
        roomId = saveReadyRoom(user);
        connectionId = start(user, roomId);
        butlerId = sample.butler().getId();
        housekeeperId = sample.housekeeper().getId();
    }

    @Test
    void getNpcMessageList_sequenceNo순_연결헤더불필요_비공개필드없음() throws Exception {

        // given
        fakeLlmClient.enqueue(FakeLlmScenario.respond("주방에 있었습니다."), FakeLlmScenario.respond("아홉 번이었습니다."));
        ask(butlerId, QUESTION);
        ask(butlerId, "종소리는 몇 번 울렸나요?");

        // when
        ResultActions result = mockMvc.perform(messagesRequest(user, roomId, butlerId));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("NPC 대화 기록 조회 성공"))
                .andExpect(jsonPath("$.data.suspectId").value(butlerId))
                .andExpect(jsonPath("$.data.suspectName").value("해리스"));
        JsonNode data = readData(result);
        assertThat(data.propertyNames()).containsExactly("suspectId", "suspectName", "messages", "pendingRequests");
        assertThat(data.get("messages").valueStream().map(node -> node.get("sequenceNo").asInt()).toList())
                .containsExactly(1, 2, 3, 4);
        assertThat(data.get("messages").valueStream().map(node -> node.get("content").asString()).toList())
                .containsExactly(QUESTION, "주방에 있었습니다.", "종소리는 몇 번 울렸나요?", "아홉 번이었습니다.");
        assertThat(data.get("pendingRequests").isEmpty()).isTrue();
        assertThat(bodyOf(result)).doesNotContain("persona", "culprit", "Culprit", "[가상 사건]", "role");
    }

    @Test
    void getNpcMessageList_대화없는NPC_빈목록() throws Exception {

        // when
        ResultActions result = mockMvc.perform(messagesRequest(user, roomId, housekeeperId));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.suspectName").value("마거릿"));
        JsonNode data = readData(result);
        assertThat(data.get("messages").isArray()).isTrue();
        assertThat(data.get("messages").isEmpty()).isTrue();
        assertThat(data.get("pendingRequests").isEmpty()).isTrue();
    }

    @Test
    void getNpcMessageList_pendingRequests_이NPC의처리중과재시도대기실패만() throws Exception {

        // given: 집사에게 실패 1건(재시도 대기), 가정부에게 처리 중 1건
        fakeLlmClient.enqueue(FakeLlmScenario.serviceError());
        String failedRequestId = newRequestId();
        mockMvc.perform(askRequest(user, roomId, butlerId, connectionId, failedRequestId, QUESTION))
                .andExpect(status().isBadGateway());
        String processingRequestId = saveProcessing(housekeeperId);

        // when
        JsonNode butler = readData(mockMvc.perform(messagesRequest(user, roomId, butlerId)));
        JsonNode housekeeper = readData(mockMvc.perform(messagesRequest(user, roomId, housekeeperId)));

        // then
        assertThat(butler.get("messages").isEmpty()).isTrue();
        assertThat(butler.get("pendingRequests")).singleElement().satisfies(pending -> {
            assertThat(pending.get("requestId").asString()).isEqualTo(failedRequestId);
            assertThat(pending.get("status").asString()).isEqualTo("FAILED");
            assertThat(pending.get("error").get("code").asString()).isEqualTo(ErrorCode.NPC_ANSWER_FAILED.name());
            assertThat(pending.get("input").get("question").asString()).isEqualTo(QUESTION);
        });
        assertThat(housekeeper.get("pendingRequests")).singleElement().satisfies(pending -> {
            assertThat(pending.get("requestId").asString()).isEqualTo(processingRequestId);
            assertThat(pending.get("status").asString()).isEqualTo("PROCESSING");
            assertThat(pending.get("targetId").asLong()).isEqualTo(housekeeperId);
        });
    }

    @Test
    void getNpcMessageList_재시도성공후_pendingRequests에서빠짐() throws Exception {

        // given
        fakeLlmClient.enqueue(FakeLlmScenario.serviceError());
        String requestId = newRequestId();
        mockMvc.perform(askRequest(user, roomId, butlerId, connectionId, requestId, QUESTION))
                .andExpect(status().isBadGateway());
        mockMvc.perform(askRequest(user, roomId, butlerId, connectionId, requestId, QUESTION))
                .andExpect(status().isOk());

        // when
        JsonNode data = readData(mockMvc.perform(messagesRequest(user, roomId, butlerId)));

        // then
        assertThat(data.get("messages")).hasSize(2);
        assertThat(data.get("pendingRequests").isEmpty()).isTrue();
    }

    @Test
    void getNpcMessageList_종료후조회() throws Exception {

        // given
        ask(butlerId, QUESTION);
        keepAliveUntil(roomId, T0.plusSeconds(LIMIT_SEC));
        roomLifecycleService.recordHeartbeat(authUserOf(user), roomId, connectionId);
        assertThat(findRoom(roomId).getStatus()).isEqualTo(RoomStatus.EXPIRED);

        // when
        ResultActions result = mockMvc.perform(messagesRequest(user, roomId, butlerId));

        // then
        result.andExpect(status().isOk());
        assertThat(readData(result).get("messages")).hasSize(2);
    }

    @Test
    void getNpcMessageList_다른사건인물_SUSPECT_NOT_FOUND_다른사용자_ROOM_NOT_FOUND() throws Exception {

        // given
        Long otherCaseSuspectId = transactionTemplate.execute(
                status -> ClocktowerCaseFixture.persistPublished(entityManager, T0).housekeeper().getId());
        User other = saveUser();

        // when
        ResultActions otherCase = mockMvc.perform(messagesRequest(user, roomId, otherCaseSuspectId));
        ResultActions otherUser = mockMvc.perform(messagesRequest(other, roomId, butlerId));

        // then
        expectError(otherCase, ErrorCode.SUSPECT_NOT_FOUND);
        expectError(otherUser, ErrorCode.ROOM_NOT_FOUND);
    }

    @Test
    void getRoomState_conversations_대화한NPC만_suspectId순_메시지수와마지막시각() throws Exception {

        // given: 가정부 1쌍(T0+10s) → 집사 2쌍(T0+20s, T0+30s). 정원사는 대화 없음
        testClock.fixAt(T0.plusSeconds(10));
        ask(housekeeperId, QUESTION);
        testClock.fixAt(T0.plusSeconds(20));
        ask(butlerId, QUESTION);
        testClock.fixAt(T0.plusSeconds(30));
        ask(butlerId, "종소리는 몇 번 울렸나요?");

        // when
        ResultActions result = mockMvc.perform(get("/api/rooms/{roomId}/state", roomId)
                .header(HttpHeaders.AUTHORIZATION, bearer(user)));

        // then
        result.andExpect(status().isOk());
        JsonNode conversations = readData(result).get("conversations");
        List<Long> expectedOrder = butlerId < housekeeperId
                ? List.of(butlerId, housekeeperId)
                : List.of(housekeeperId, butlerId);
        assertThat(conversations.valueStream().map(node -> node.get("suspectId").asLong()).toList())
                .containsExactlyElementsOf(expectedOrder);
        JsonNode butler = conversations.valueStream()
                .filter(node -> node.get("suspectId").asLong() == butlerId).findFirst().orElseThrow();
        JsonNode housekeeper = conversations.valueStream()
                .filter(node -> node.get("suspectId").asLong() == housekeeperId).findFirst().orElseThrow();
        assertThat(butler.propertyNames()).containsExactly("suspectId", "messageCount", "lastMessageAt");
        assertThat(butler.get("messageCount").asInt()).isEqualTo(4);
        assertThat(butler.get("lastMessageAt").asString()).isEqualTo("2026-11-12T14:00:30");
        assertThat(housekeeper.get("messageCount").asInt()).isEqualTo(2);
        assertThat(housekeeper.get("lastMessageAt").asString()).isEqualTo("2026-11-12T14:00:10");
    }

    // --- helpers ---

    private void ask(Long suspectId, String question) throws Exception {
        mockMvc.perform(askRequest(user, roomId, suspectId, connectionId, newRequestId(), question))
                .andExpect(status().isOk());
    }

    private String saveProcessing(Long suspectId) {
        String requestId = newRequestId();
        transactionTemplate.executeWithoutResult(status -> entityManager.persist(new AiRequest(
                entityManager.getReference(Room.class, roomId), requestId, AiRequestType.NPC_ASK, suspectId,
                AiRequestInput.question(QUESTION), T0.plus(Duration.ofSeconds(1)))));
        return requestId;
    }
}
