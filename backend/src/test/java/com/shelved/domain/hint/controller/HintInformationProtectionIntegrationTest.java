package com.shelved.domain.hint.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shelved.common.entity.EvidenceInspection;
import com.shelved.common.entity.Evidence;
import com.shelved.common.entity.Room;
import com.shelved.common.entity.json.AiRequestInput;
import com.shelved.domain.hint.HintIntegrationTestSupport;
import com.shelved.fixture.ClocktowerCaseFixture;
import com.shelved.infra.llm.FakeLlmScenario;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;

/**
 * 힌트 컨텍스트(정답·트릭·물증 설명·NPC 대화 원문)와 질문·답변이 API 응답·input_json·애플리케이션 로그에 새지 않는지 확인한다
 */
@ExtendWith(OutputCaptureExtension.class)
class HintInformationProtectionIntegrationTest extends HintIntegrationTestSupport {

    private static final String SECRET_QUESTION = "비밀표식질문-서재 시계는 왜 멈췄나요?";
    private static final String SECRET_ANSWER = "비밀표식답변-시계가 멈춘 시각을 다른 진술과 비교해 보세요.";
    private static final String SECRET_FOLLOWUP = "비밀표식보충-비교하라는 게 무슨 뜻이죠?";
    private static final String SECRET_CONVERSATION = "비밀표식대화";

    @Test
    void createHint_컨텍스트가응답과input_json과로그에없음(CapturedOutput output) throws Exception {

        // given: 조사 기록·NPC 대화가 있는 세션. 성공·본 모델 실패·형식 오류·보충 질문을 모두 거친다
        inspectSmallBox();
        saveConversation(roomId, sample.butler().getId(), 2, SECRET_CONVERSATION);
        fakeLlmClient.enqueue(
                FakeLlmScenario.serviceError(),
                FakeLlmScenario.respond(" "),
                FakeLlmScenario.respond(SECRET_ANSWER),
                FakeLlmScenario.respond("비밀표식보충답변"));
        String requestId = newRequestId();
        List<String> bodyList = new ArrayList<>();

        // when
        bodyList.add(bodyOf(mockMvc.perform(hintRequest(user, roomId, connectionId, requestId, SECRET_QUESTION))));
        bodyList.add(bodyOf(mockMvc.perform(hintRequest(user, roomId, connectionId, requestId, SECRET_QUESTION))));
        String created = bodyOf(mockMvc.perform(hintRequest(user, roomId, connectionId, requestId, SECRET_QUESTION))
                .andExpect(status().isOk()));
        Long hintId = readHintId(created);
        bodyList.add(created);
        bodyList.add(bodyOf(mockMvc.perform(followupRequest(user, roomId, hintId, connectionId, newRequestId(), SECRET_FOLLOWUP))
                .andExpect(status().isOk())));
        bodyList.add(bodyOf(mockMvc.perform(hintListRequest(user, roomId))));
        bodyList.add(bodyOf(mockMvc.perform(stateRequest(user, roomId))));
        bodyList.add(bodyOf(mockMvc.perform(get("/api/rooms/{roomId}/ai-requests/{requestId}", roomId, requestId)
                .header(HttpHeaders.AUTHORIZATION, bearer(user)))));

        // then: 프롬프트에는 들어갔지만
        assertThat(fakeLlmClient.requestList().stream()
                .filter(request -> request.systemPrompt() != null && request.systemPrompt().contains(ClocktowerCaseFixture.TRICK_SUMMARY)))
                .isNotEmpty();
        // 응답에는 정답·내부 컨텍스트가 없고
        List<String> contextSecrets = List.of(
                ClocktowerCaseFixture.TRICK_SUMMARY,
                ClocktowerCaseFixture.TRICK_ELEMENT_DESCRIPTION,
                "범인: 해리스",
                sample.smallBox().getDescription(),
                SECRET_CONVERSATION,
                sample.butler().getPersonaPrompt(),
                "정답 참고",
                "수사 기록");
        assertThat(bodyList).allSatisfy(body -> assertThat(body).doesNotContain(contextSecrets));
        // input_json에는 플레이어 질문만 있고
        assertThat(findAiRequest(roomId, requestId).getInputJson()).isEqualTo(AiRequestInput.question(SECRET_QUESTION));
        // 로그에는 컨텍스트·질문·답변이 없다
        assertThat(output.getAll()).doesNotContain(contextSecrets)
                .doesNotContain(SECRET_QUESTION, SECRET_ANSWER, SECRET_FOLLOWUP, "비밀표식보충답변", "비밀표식");
        assertThat(output.getAll()).contains(requestId);
    }

    private void inspectSmallBox() {
        transactionTemplate.executeWithoutResult(status -> entityManager.persist(new EvidenceInspection(
                entityManager.find(Room.class, roomId), entityManager.getReference(Evidence.class, sample.smallBox().getId()), T0)));
    }

    private static Long readHintId(String body) {
        return JSON_MAPPER.readTree(body).get("data").get("hint").get("hintId").asLong();
    }
}
