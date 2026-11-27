package com.shelved.domain.accusation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shelved.common.entity.Accusation;
import com.shelved.common.entity.AiRequest;
import com.shelved.common.entity.LlmUsageLog;
import com.shelved.common.entity.User;
import com.shelved.common.entity.json.AiRequestInput;
import com.shelved.common.enums.AiRequestStatus;
import com.shelved.common.enums.GradingStatus;
import com.shelved.common.enums.LlmRequestType;
import com.shelved.common.enums.RoomStatus;
import com.shelved.common.exception.ErrorCode;
import com.shelved.domain.accusation.AccusationIntegrationTestSupport;
import com.shelved.fixture.ClocktowerCaseFixture;
import com.shelved.infra.llm.FakeLlmClient;
import com.shelved.infra.llm.FakeLlmScenario;
import com.shelved.infra.llm.LlmRequest;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

/**
 * POST·GET /api/rooms/{roomId}/accusations의 정상 처리·입력 검증·권한·정보 보호·Fake 기본 채점
 */
@ExtendWith(OutputCaptureExtension.class)
class AccusationCreateIntegrationTest extends AccusationIntegrationTestSupport {

    // 응답·로그에 나오면 안 되는 내부 정보 (채점 기준·정답·내부 필드 이름)
    private static final String[] FORBIDDEN_TEXTS = {
            "elementResults", "element_results", "culpritCorrect", "culprit_correct", "trk-1-i1", "criterion",
            "녹음 장치를 사용했음을 밝힘", ClocktowerCaseFixture.TRICK_SUMMARY, ClocktowerCaseFixture.TRICK_ELEMENT_DESCRIPTION,
            "판단 이유", "isCulprit"
    };

    // --- 정상 처리 ---

    @Test
    void createAccusation_부분정답_응답필드_스냅샷_사용량로그_힌트불변() throws Exception {

        // given: 시작 65.5초 뒤 제출
        keepAliveUntil(roomId, atElapsed(65_500L));
        fakeLlmClient.enqueue(FakeLlmScenario.respond(partialGrading()));
        String requestId = newRequestId();

        // when
        ResultActions result = mockMvc.perform(accuseButler(requestId));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("지목 채점 성공"))
                .andExpect(jsonPath("$.data.accusation.requestId").value(requestId))
                .andExpect(jsonPath("$.data.accusation.suspectId").value(sample.butler().getId()))
                .andExpect(jsonPath("$.data.accusation.suspectName").value("해리스"))
                .andExpect(jsonPath("$.data.accusation.trickExplanation").value(TRICK))
                .andExpect(jsonPath("$.data.accusation.gradingStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.accusation.verdict").value("PARTIAL"))
                .andExpect(jsonPath("$.data.accusation.feedback").value(FEEDBACK))
                .andExpect(jsonPath("$.data.accusation.attemptNo").value(1))
                .andExpect(jsonPath("$.data.accusation.retryAvailableAt").isEmpty())
                .andExpect(jsonPath("$.data.accusation.superseded").value(false))
                .andExpect(jsonPath("$.data.accusationCount.limit").value(3))
                .andExpect(jsonPath("$.data.accusationCount.used").value(1))
                .andExpect(jsonPath("$.data.accusationCount.reserved").value(0))
                .andExpect(jsonPath("$.data.accusationCount.remaining").value(2))
                .andExpect(jsonPath("$.data.roomStatus").value("IN_PROGRESS"));
        JsonNode data = readData(result);
        assertThat(data.propertyNames()).containsExactly("accusation", "accusationCount", "roomStatus");
        assertThat(data.get("accusation").propertyNames()).containsExactly(
                "accusationId", "requestId", "suspectId", "suspectName", "trickExplanation", "submittedAt",
                "gradingStatus", "verdict", "feedback", "attemptNo", "retryAvailableAt", "superseded");
        assertThat(bodyOf(result)).doesNotContain(FORBIDDEN_TEXTS);

        Accusation accusation = findAccusation(roomId, requestId);
        assertThat(accusation.getGradingStatus()).isEqualTo(GradingStatus.COMPLETED);
        assertThat(accusation.getSubmittedAt()).isEqualTo(atElapsed(65_500L));
        assertThat(accusation.getSubmittedActiveMs()).isEqualTo(65_500L);
        assertThat(accusation.getCulpritCorrect()).isTrue();
        assertThat(accusation.getElementResults()).hasSize(2);
        AiRequest aiRequest = findAiRequest(roomId, requestId);
        assertThat(aiRequest.getStatus()).isEqualTo(AiRequestStatus.SUCCEEDED);
        assertThat(aiRequest.getTargetId()).isNull();
        assertThat(aiRequest.getInputJson()).isEqualTo(AiRequestInput.accusation(sample.butler().getId(), TRICK));
        assertThat(aiRequest.getResultRefId()).isEqualTo(accusation.getId());
        assertThat(aiRequest.getResultJson()).isEqualTo(data);

        // 질문 분류 없이 채점 1회, 사용량 로그 GRADING
        assertThat(fakeLlmClient.requestList()).extracting(LlmRequest::requestType).containsExactly(LlmRequestType.GRADING);
        assertThat(findUsageLogList(roomId)).extracting(LlmUsageLog::getRequestType).containsExactly(LlmRequestType.GRADING);
        // 지목 피드백은 힌트를 만들지 않는다
        assertThat(findHintList(roomId)).isEmpty();
        mockMvc.perform(stateRequest(user, roomId))
                .andExpect(jsonPath("$.data.hintCount.used").value(0))
                .andExpect(jsonPath("$.data.hintCount.remaining").value(5))
                .andExpect(jsonPath("$.data.accusationCount.used").value(1))
                .andExpect(jsonPath("$.data.pendingAccusation").isEmpty());
    }

    @Test
    void createAccusation_같은requestId재전송_저장된스냅샷_다시채점안함() throws Exception {

        // given
        fakeLlmClient.enqueue(FakeLlmScenario.respond(partialGrading()));
        String requestId = newRequestId();
        String first = bodyOf(mockMvc.perform(accuseButler(requestId)).andExpect(status().isOk()));
        testClock.advance(Duration.ofSeconds(3));

        // when
        ResultActions again = mockMvc.perform(accuseButler(requestId));
        ResultActions statusResult = mockMvc.perform(aiRequestStatusRequest(requestId));

        // then
        again.andExpect(status().isOk());
        assertThat(readData(again)).isEqualTo(JSON_MAPPER.readTree(first).get("data"));
        statusResult.andExpect(jsonPath("$.data.type").value("ACCUSATION"))
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.input.suspectId").value(sample.butler().getId()))
                .andExpect(jsonPath("$.data.input.trickExplanation").value(TRICK))
                .andExpect(jsonPath("$.data.result.accusation.verdict").value("PARTIAL"));
        assertThat(readData(statusResult).get("input").propertyNames()).containsExactlyInAnyOrder("suspectId", "trickExplanation");
        assertThat(fakeLlmClient.callCount()).isEqualTo(1);
        assertThat(findAccusationList(roomId)).hasSize(1);
    }

    @Test
    void createAccusation_같은requestId다른답안_AI_REQUEST_ID_REUSED() throws Exception {

        // given
        String requestId = newRequestId();
        fakeLlmClient.enqueue(FakeLlmScenario.respond(partialGrading()));
        mockMvc.perform(accuseButler(requestId)).andExpect(status().isOk());

        // when
        ResultActions result = mockMvc.perform(accusationRequest(
                user, roomId, connectionId, requestId, sample.butler().getId(), TRICK + " 추가"));

        // then
        expectError(result, ErrorCode.AI_REQUEST_ID_REUSED);
    }

    // --- Fake 기본 채점 (시나리오 없음 → 모든 항목 MET) ---

    @Test
    void createAccusation_Fake기본채점_범인맞음_CLEARED_기록시간은접수시점() throws Exception {

        // given
        keepAliveUntil(roomId, atElapsed(65_900L));

        // when
        ResultActions result = mockMvc.perform(accuseButler(newRequestId()));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accusation.verdict").value("CORRECT"))
                .andExpect(jsonPath("$.data.accusation.feedback").value(FakeLlmClient.FAKE_GRADING_FEEDBACK))
                .andExpect(jsonPath("$.data.roomStatus").value("CLEARED"));
        assertThat(findRoom(roomId).getStatus()).isEqualTo(RoomStatus.CLEARED);
        assertThat(findPlayResultList(roomId)).singleElement().satisfies(playResult -> {
            assertThat(playResult.isCleared()).isTrue();
            assertThat(playResult.getDurationSec()).isEqualTo(65);
            assertThat(playResult.getHintUsedCount()).isZero();
            assertThat(playResult.getEndedAt()).isEqualTo(atElapsed(65_900L));
        });
    }

    @Test
    void createAccusation_Fake기본채점_범인틀림3회_FAILED_결과1건_이후지목거부() throws Exception {

        // given
        Long housekeeperId = sample.housekeeper().getId();
        for (int n = 1; n <= 2; n++) {
            keepAliveUntil(roomId, atElapsed(n * 100_000L));
            mockMvc.perform(accusationRequest(user, roomId, connectionId, newRequestId(), housekeeperId, TRICK))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accusation.verdict").value("INCORRECT"))
                    .andExpect(jsonPath("$.data.accusation.attemptNo").value(n))
                    .andExpect(jsonPath("$.data.roomStatus").value("IN_PROGRESS"));
        }
        keepAliveUntil(roomId, atElapsed(300_400L));

        // when
        ResultActions third = mockMvc.perform(accusationRequest(user, roomId, connectionId, newRequestId(), housekeeperId, TRICK));
        ResultActions fourth = mockMvc.perform(accuseButler(newRequestId()));

        // then
        third.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accusation.attemptNo").value(3))
                .andExpect(jsonPath("$.data.accusationCount.used").value(3))
                .andExpect(jsonPath("$.data.accusationCount.remaining").value(0))
                .andExpect(jsonPath("$.data.roomStatus").value("FAILED"));
        expectError(fourth, ErrorCode.ROOM_NOT_IN_PROGRESS);
        assertThat(findPlayResultList(roomId)).singleElement().satisfies(playResult -> {
            assertThat(playResult.isCleared()).isFalse();
            assertThat(playResult.getDurationSec()).isEqualTo(300);
        });
        assertThat(findRoom(roomId).getStatus()).isEqualTo(RoomStatus.FAILED);
        assertThat(findRoom(roomId).getElapsedActiveMs()).isEqualTo(300_400L);
    }

    @Test
    void createAccusation_정상채점3회후_ACCUSATION_LIMIT_EXCEEDED_AI호출없음() throws Exception {

        // given: 수사 중인데 이미 3회 정상 채점 완료
        saveCompletedAccusations(roomId, 3);

        // when
        ResultActions result = mockMvc.perform(accuseButler(newRequestId()));

        // then
        expectError(result, ErrorCode.ACCUSATION_LIMIT_EXCEEDED);
        assertThat(fakeLlmClient.callCount()).isZero();
        assertThat(findAccusationList(roomId)).hasSize(3);
        assertThat(countAiRequests(roomId)).isZero();
    }

    // --- 입력 검증 ---

    @Test
    void createAccusation_트릭설명길이_앞뒤공백뺀길이로검사_원문저장() throws Exception {

        // given: 앞뒤 공백(줄바꿈 없는 공백 포함)을 빼면 9자 / 3000자 / 3001자
        String nineChars = "   " + "가".repeat(9) + "  \n";
        String maxChars = "  " + "나".repeat(3000) + "　";
        String overChars = "다".repeat(3001);

        // when
        ResultActions tooShort = mockMvc.perform(accusationRequest(user, roomId, connectionId, newRequestId(), sample.butler().getId(), nineChars));
        ResultActions tooLong = mockMvc.perform(accusationRequest(user, roomId, connectionId, newRequestId(), sample.butler().getId(), overChars));
        ResultActions blank = mockMvc.perform(accusationRequest(user, roomId, connectionId, newRequestId(), sample.butler().getId(), "   \n  "));
        ResultActions badRequestId = mockMvc.perform(accusationRequest(user, roomId, connectionId, "not-a-uuid", sample.butler().getId(), TRICK));
        ResultActions noSuspect = mockMvc.perform(accusationRequest(user, roomId, connectionId, newRequestId(), null, TRICK));
        String maxRequestId = newRequestId();
        fakeLlmClient.enqueue(FakeLlmScenario.respond(partialGrading()));
        ResultActions max = mockMvc.perform(accusationRequest(user, roomId, connectionId, maxRequestId, sample.butler().getId(), maxChars));

        // then
        expectInvalid(tooShort, "트릭 설명은 10~3000자로 입력해 주세요.");
        expectInvalid(tooLong, "트릭 설명은 10~3000자로 입력해 주세요.");
        expectInvalid(blank, "트릭 설명은 필수입니다.");
        expectInvalid(badRequestId, "요청 식별자 형식이 올바르지 않습니다.");
        expectInvalid(noSuspect, "지목할 인물을 선택해 주세요.");
        max.andExpect(status().isOk());
        assertThat(findAccusation(roomId, maxRequestId).getTrickExplanation()).isEqualTo(maxChars);
        assertThat(findAccusationList(roomId)).hasSize(1);
    }

    @Test
    void createAccusation_다른사건인물_SUSPECT_NOT_FOUND_기록없음() throws Exception {

        // given: 다른 사건의 인물
        Long otherCaseSuspectId = transactionTemplate.execute(status ->
                ClocktowerCaseFixture.persistPublished(entityManager, T0).butler().getId());

        // when
        ResultActions result = mockMvc.perform(accusationRequest(
                user, roomId, connectionId, newRequestId(), otherCaseSuspectId, TRICK));
        ResultActions missing = mockMvc.perform(accusationRequest(
                user, roomId, connectionId, newRequestId(), 987_654_321L, TRICK));

        // then
        expectError(result, ErrorCode.SUSPECT_NOT_FOUND);
        expectError(missing, ErrorCode.SUSPECT_NOT_FOUND);
        assertThat(findAccusationList(roomId)).isEmpty();
        assertThat(countAiRequests(roomId)).isZero();
        assertThat(fakeLlmClient.callCount()).isZero();
    }

    @Test
    void createAccusation_다른사용자세션_ROOM_NOT_FOUND() throws Exception {

        // given
        User other = saveUser();

        // when
        ResultActions post = mockMvc.perform(accusationRequest(
                other, roomId, connectionId, newRequestId(), sample.butler().getId(), TRICK));
        ResultActions list = mockMvc.perform(accusationListRequest(other, roomId));

        // then
        expectError(post, ErrorCode.ROOM_NOT_FOUND);
        expectError(list, ErrorCode.ROOM_NOT_FOUND);
        assertThat(findAccusationList(roomId)).isEmpty();
    }

    @Test
    void createAccusation_새답안연결헤더없음_CONNECTION_ID_REQUIRED() throws Exception {

        // when
        ResultActions result = mockMvc.perform(accusationRequest(
                user, roomId, null, newRequestId(), sample.butler().getId(), TRICK));

        // then
        expectError(result, ErrorCode.CONNECTION_ID_REQUIRED);
        assertThat(findAccusationList(roomId)).isEmpty();
    }

    // --- 조회 ---

    @Test
    void getAccusationList_오래된순_attemptNo_정보보호() throws Exception {

        // given: 부분 정답 1건 → 채점 실패 1건
        fakeLlmClient.enqueue(FakeLlmScenario.respond(partialGrading()), FakeLlmScenario.serviceError());
        String completedId = newRequestId();
        String failedId = newRequestId();
        mockMvc.perform(accuseButler(completedId)).andExpect(status().isOk());
        testClock.advance(Duration.ofSeconds(2));
        mockMvc.perform(accuseButler(failedId)).andExpect(status().isBadGateway());

        // when
        ResultActions result = mockMvc.perform(accusationListRequest(user, roomId));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("지목 기록 조회 성공"))
                .andExpect(jsonPath("$.data.accusationCount.used").value(1))
                .andExpect(jsonPath("$.data.accusationCount.reserved").value(0))
                .andExpect(jsonPath("$.data.accusations.length()").value(2))
                .andExpect(jsonPath("$.data.accusations[0].requestId").value(completedId))
                .andExpect(jsonPath("$.data.accusations[0].attemptNo").value(1))
                .andExpect(jsonPath("$.data.accusations[1].requestId").value(failedId))
                .andExpect(jsonPath("$.data.accusations[1].gradingStatus").value("ERROR"))
                .andExpect(jsonPath("$.data.accusations[1].verdict").isEmpty())
                .andExpect(jsonPath("$.data.accusations[1].feedback").isEmpty())
                .andExpect(jsonPath("$.data.accusations[1].attemptNo").isEmpty())
                .andExpect(jsonPath("$.data.accusations[1].retryAvailableAt").value("2026-11-12T14:00:12"))
                .andExpect(jsonPath("$.data.accusations[1].superseded").value(false));
        assertThat(readData(result).propertyNames()).containsExactly("accusationCount", "accusations");
        assertThat(bodyOf(result)).doesNotContain(FORBIDDEN_TEXTS);
    }

    // --- 정보 보호: 로그 ---

    @Test
    void createAccusation_답안과피드백은로그에남지않음(CapturedOutput output) throws Exception {

        // given: 성공 1건, 형식 오류 1건
        String marker = "로그확인용답안-" + UUID.randomUUID();
        String answer = TRICK + " " + marker;
        fakeLlmClient.enqueue(FakeLlmScenario.respond(partialGrading()), FakeLlmScenario.respond("채점 결과는 이렇습니다."));
        // 같은 클래스의 앞선 테스트 출력은 보지 않는다
        int outputStart = output.getAll().length();

        // when
        mockMvc.perform(accusationRequest(user, roomId, connectionId, newRequestId(), sample.butler().getId(), answer))
                .andExpect(status().isOk());
        mockMvc.perform(accusationRequest(user, roomId, connectionId, newRequestId(), sample.butler().getId(), answer + "2"))
                .andExpect(status().isBadGateway());

        // then
        assertThat(output.getAll().substring(outputStart))
                .contains("AI 응답 형식 오류")
                .doesNotContain(marker, FEEDBACK, "채점 결과는 이렇습니다", "녹음 장치를 사용했음을 밝힘");
    }

    @Test
    void createAccusation_시드오류_500_기록없음_로그는사건ID와구분만(CapturedOutput output) throws Exception {

        // given: 핵심 요소가 없는 사건 (시드 오류)
        transactionTemplate.executeWithoutResult(status -> entityManager.createQuery(
                        "delete from CaseTrickElement e where e.mysteryCase.id = :caseId")
                .setParameter("caseId", sample.mysteryCase().getId())
                .executeUpdate());
        String marker = "시드오류확인용답안-" + UUID.randomUUID();
        int outputStart = output.getAll().length();

        // when
        ResultActions result = mockMvc.perform(accusationRequest(
                user, roomId, connectionId, newRequestId(), sample.butler().getId(), TRICK + marker));

        // then
        expectError(result, ErrorCode.INTERNAL_SERVER_ERROR);
        assertThat(findAccusationList(roomId)).isEmpty();
        assertThat(countAiRequests(roomId)).isZero();
        assertThat(fakeLlmClient.callCount()).isZero();
        assertThat(output.getAll().substring(outputStart))
                .contains("채점 컨텍스트 구성 실패 caseId=" + sample.mysteryCase().getId(), "핵심 요소가 없는 사건")
                .doesNotContain(marker);
    }

    private static void expectInvalid(ResultActions result, String message) throws Exception {
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value(message));
    }
}
