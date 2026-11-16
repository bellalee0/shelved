package com.shelved.domain.npc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shelved.common.entity.NpcMessage;
import com.shelved.common.entity.User;
import com.shelved.common.exception.ErrorCode;
import com.shelved.domain.airequest.AiRequestIntegrationTestSupport;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * NPC 질문·대화 기록 통합 테스트 공통 준비. 가상 사건 「시계탑 저택의 마지막 종소리」만 쓴다
 */
public abstract class NpcIntegrationTestSupport extends AiRequestIntegrationTestSupport {

    protected static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    protected static final String CONNECTION_ID_HEADER = "X-Connection-Id";
    protected static final String QUESTION = "어젯밤 9시에 서재 근처에 있었나요?";

    @Autowired
    protected MockMvc mockMvc;

    protected MockHttpServletRequestBuilder askRequest(
            User owner,
            Long roomId,
            Long suspectId,
            String connectionId,
            String requestId,
            String question
    ) {
        MockHttpServletRequestBuilder builder = post("/api/rooms/{roomId}/npc/{suspectId}/ask", roomId, suspectId)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON_MAPPER.writeValueAsString(Map.of("requestId", requestId, "question", question)));
        if (connectionId != null) {
            builder.header(CONNECTION_ID_HEADER, connectionId);
        }
        return builder;
    }

    protected MockHttpServletRequestBuilder messagesRequest(User owner, Long roomId, Long suspectId) {
        return get("/api/rooms/{roomId}/npc/{suspectId}/messages", roomId, suspectId)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner));
    }

    protected List<NpcMessage> findMessageList(Long roomId, Long suspectId) {
        return transactionTemplate.execute(status -> entityManager.createQuery(
                        "select m from NpcMessage m join m.conversation c "
                                + "where c.room.id = :roomId and c.suspect.id = :suspectId order by m.sequenceNo", NpcMessage.class)
                .setParameter("roomId", roomId)
                .setParameter("suspectId", suspectId)
                .getResultList());
    }

    protected long countAiRequests(Long roomId) {
        return transactionTemplate.execute(status -> entityManager.createQuery(
                        "select count(a) from AiRequest a where a.room.id = :roomId", Long.class)
                .setParameter("roomId", roomId)
                .getSingleResult());
    }

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
}
