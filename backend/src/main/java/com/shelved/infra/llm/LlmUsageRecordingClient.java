package com.shelved.infra.llm;

import com.shelved.common.enums.LlmCallStatus;
import com.shelved.common.utils.SafeExceptionLog;
import com.shelved.domain.llmusage.service.LlmUsageLogService;
import com.shelved.domain.llmusage.service.LlmUsageRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 도메인 서비스가 LLM을 호출하는 유일한 진입점. LlmClient를 감싸 성공·실패와 관계없이 호출마다 사용량을 기록한다.
 * - 사용량 기록은 별도 짧은 트랜잭션(REQUIRES_NEW)이며, 기록 실패는 로그만 남기고 호출 결과를 바꾸지 않는다
 * - DB 트랜잭션 안에서 호출하면 바로 실패한다 (backend.md 13: AI 응답을 기다리는 동안 트랜잭션·잠금을 잡지 않는다)
 * - 로그에는 요청 유형·상태·토큰 수·오류 코드만 남긴다
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmUsageRecordingClient {

    static final String UNEXPECTED_ERROR_CODE = "LLM_UNEXPECTED_ERROR";

    private final LlmClient llmClient;
    private final LlmUsageLogService llmUsageLogService;

    public LlmResponse call(LlmRequest request, LlmCallContext context) {

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("LLM 호출은 DB 트랜잭션 밖에서 해야 합니다.");
        }

        String model = llmClient.modelOf(request.modelTier());
        LlmResponse response;
        try {
            response = llmClient.call(request);
        } catch (LlmException e) {
            record(request, context, model, null, LlmCallStatus.FAILED, e.getErrorType().name());
            throw e;
        } catch (RuntimeException e) {
            record(request, context, model, null, LlmCallStatus.FAILED, UNEXPECTED_ERROR_CODE);
            throw e;
        }

        record(request, context, response.model(), response, LlmCallStatus.SUCCEEDED, null);

        return response;
    }

    private void record(
            LlmRequest request,
            LlmCallContext context,
            String model,
            LlmResponse response,
            LlmCallStatus callStatus,
            String errorCode
    ) {
        Integer inputTokens = response == null ? null : response.inputTokens();
        Integer outputTokens = response == null ? null : response.outputTokens();
        // 한쪽만 확인된 사용량은 미확인으로 본다 (usage_known=false, 토큰 null)
        if (inputTokens == null || outputTokens == null) {
            inputTokens = null;
            outputTokens = null;
        }

        try {
            llmUsageLogService.recordUsage(new LlmUsageRecord(
                    request.requestType(),
                    model == null || model.isBlank() ? "unknown" : model,
                    context.roomId(),
                    context.caseId(),
                    inputTokens,
                    outputTokens,
                    callStatus,
                    errorCode
            ));
        } catch (RuntimeException e) {
            log.error("LLM 사용량 기록 실패 requestType={} callStatus={} errorCode={} exception={}{}",
                    request.requestType(), callStatus, errorCode,
                    SafeExceptionLog.causeChain(e), SafeExceptionLog.rootCauseFrames(e));
        }
    }
}
