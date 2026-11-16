package com.shelved.domain.airequest.service;

import static com.shelved.common.exception.ErrorCode.QUESTION_BLOCKED;

import com.shelved.common.enums.LlmRequestType;
import com.shelved.common.exception.CustomException;
import com.shelved.common.exception.ErrorCode;
import com.shelved.domain.airequest.dto.AiRequestFailureDetail;
import com.shelved.infra.llm.LlmCallContext;
import com.shelved.infra.llm.LlmException;
import com.shelved.infra.llm.LlmMessage;
import com.shelved.infra.llm.LlmModelTier;
import com.shelved.infra.llm.LlmRequest;
import com.shelved.infra.llm.LlmResponse;
import com.shelved.infra.llm.LlmUsageRecordingClient;
import com.shelved.infra.llm.PromptTemplateLoader;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/**
 * 직접적인 정답 요구 차단 (backend.md AI 연동 규칙). NPC 질문·새 힌트·보충 질문이 AI 요청을 접수하기 전에 호출한다.
 *
 * <ul>
 *     <li>분류 모델(shelved.llm.filter-model)에 의도 분류를 1회 요청한다. 금칙어·정규식으로 대신하지 않는다</li>
 *     <li>허용이면 그대로 반환, 차단이면 400 QUESTION_BLOCKED</li>
 *     <li>분류 호출 실패·시간 초과·응답 형식 오류면 호출한 도메인이 정한 502 코드(failureCode)와 detail { requestId }</li>
 *     <li>사용량은 LlmUsageRecordingClient가 QUESTION_FILTER로 기록한다(성공·실패 모두)</li>
 * </ul>
 * 트랜잭션 밖에서 호출해야 한다. 질문 내용은 로그에 남기지 않는다
 *
 * <pre>
 * // 사용 예 (힌트): 접수 전에 호출하고, 통과하면 AiRequestExecutor.execute
 * questionFilterService.validateQuestion(question, requestId, LlmCallContext.game(roomId, caseId), HINT_GENERATION_FAILED);
 * </pre>
 */
@Slf4j
@Service
public class QuestionFilterService {

    static final String PROMPT_NAME = "question-filter.txt";
    // 응답은 {"blocked": true|false} 하나라 짧게 둔다
    static final int MAX_OUTPUT_TOKENS = 64;

    private final LlmUsageRecordingClient llmUsageRecordingClient;
    private final PromptTemplateLoader promptTemplateLoader;
    private final QuestionFilterResponseParser responseParser;

    public QuestionFilterService(
            LlmUsageRecordingClient llmUsageRecordingClient,
            PromptTemplateLoader promptTemplateLoader,
            JsonMapper jsonMapper
    ) {
        this.llmUsageRecordingClient = llmUsageRecordingClient;
        this.promptTemplateLoader = promptTemplateLoader;
        this.responseParser = new QuestionFilterResponseParser(jsonMapper);
    }

    /**
     * @param question    플레이어가 보낸 질문 원문
     * @param requestId   실패 응답 detail에 담을 요청 ID
     * @param callContext 사용량 로그의 room_id·case_id
     * @param failureCode 분류 실패 시 던질 502 코드 (NPC_ANSWER_FAILED, HINT_GENERATION_FAILED, HINT_FOLLOWUP_FAILED)
     */
    public void validateQuestion(String question, String requestId, LlmCallContext callContext, ErrorCode failureCode) {

        LlmRequest request = new LlmRequest(
                LlmRequestType.QUESTION_FILTER,
                LlmModelTier.FILTER,
                promptTemplateLoader.load(PROMPT_NAME),
                List.of(LlmMessage.user(question)),
                MAX_OUTPUT_TOKENS
        );

        LlmResponse response;
        try {
            response = llmUsageRecordingClient.call(request, callContext);
        } catch (LlmException e) {
            log.warn("질문 분류 호출 실패 requestId={} errorType={}", requestId, e.getErrorType());
            throw new CustomException(failureCode, new AiRequestFailureDetail(requestId));
        }

        boolean blocked;
        try {
            blocked = responseParser.parseBlocked(response.text());
        } catch (AiResponseInvalidException e) {
            log.warn("질문 분류 응답 형식 오류 requestId={} reason={}", requestId, e.getMessage());
            throw new CustomException(failureCode, new AiRequestFailureDetail(requestId));
        }

        if (blocked) {
            log.info("직접적인 정답 요구 질문 차단 requestId={}", requestId);
            throw new CustomException(QUESTION_BLOCKED);
        }
    }
}
