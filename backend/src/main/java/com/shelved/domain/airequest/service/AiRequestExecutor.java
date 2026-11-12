package com.shelved.domain.airequest.service;

import static com.shelved.common.exception.ErrorCode.ROOM_ENDED_DURING_REQUEST;

import com.shelved.common.exception.CustomException;
import com.shelved.common.exception.ErrorCode;
import com.shelved.common.jwt.AuthUser;
import com.shelved.common.utils.SafeExceptionLog;
import com.shelved.domain.airequest.service.AiRequestAcceptance.InProgress;
import com.shelved.domain.airequest.service.AiRequestAcceptance.Replayed;
import com.shelved.domain.airequest.service.AiRequestAcceptance.Started;
import com.shelved.infra.llm.LlmCallContext;
import com.shelved.infra.llm.LlmException;
import com.shelved.infra.llm.LlmRequest;
import com.shelved.infra.llm.LlmResponse;
import com.shelved.infra.llm.LlmUsageRecordingClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 게임플레이 AI 요청 공통 흐름 (backend.md 13): ① 짧은 트랜잭션에서 접수·임시 확보 → ② 트랜잭션 밖에서 AI 호출 → ③ 짧은 트랜잭션에서 확정.
 * 도메인 서비스는 트랜잭션 없이 execute를 호출하고, 컨트롤러는 결과의 toResponseEntity로 200·202를 구분한다.
 *
 * <ul>
 *     <li>AI 호출 실패·시간 초과·형식 오류(LlmException, AiResponseInvalidException): FAILED·임시 확보 해제 후 502 유형별 코드</li>
 *     <li>세션이 최종 종료된 뒤 도착한 응답: 반영하지 않고 409 ROOM_ENDED_DURING_REQUEST</li>
 *     <li>이전 실행의 늦은 응답·늦은 실패: 반영하지 않고 요청의 현재 상태로 응답</li>
 *     <li>그 밖의 예외: 요청을 FAILED로 정리한 뒤 원래 예외를 다시 던진다</li>
 * </ul>
 * 로그에는 요청 ID·유형·실행 번호·오류 구분만 남긴다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiRequestExecutor {

    private final AiRequestTransactionService aiRequestTransactionService;
    private final LlmUsageRecordingClient llmUsageRecordingClient;

    public <C> AiRequestResult execute(AuthUser authUser, AiRequestCommand command, AiRequestHandler<C> handler) {

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("AI 요청은 트랜잭션 밖에서 실행해야 합니다.");
        }

        AiRequestAcceptance<C> acceptance = aiRequestTransactionService.accept(authUser, command, handler);
        AiRequestExecution<C> execution;
        switch (acceptance) {
            case Replayed<C> replayed -> {
                return new AiRequestResult.Completed(replayed.result());
            }
            case InProgress<C> inProgress -> {
                return new AiRequestResult.Processing(inProgress.status());
            }
            case Started<C> started -> execution = started.execution();
        }

        LlmRequest llmRequest;
        try {
            llmRequest = handler.buildLlmRequest(execution.context());
        } catch (RuntimeException e) {
            failQuietly(execution, handler, e);
            throw e;
        }

        LlmResponse response;
        try {
            response = llmUsageRecordingClient.call(llmRequest, LlmCallContext.game(execution.roomId(), execution.caseId()));
        } catch (RuntimeException e) {
            log.warn("AI 호출 실패 requestId={} type={} executionNo={} errorType={}",
                    execution.requestId(), handler.type(), execution.executionNo(), errorTypeOf(e));
            return failExecution(execution, handler);
        }

        AiRequestCompletion completion;
        try {
            completion = aiRequestTransactionService.complete(execution, handler, response);
        } catch (AiResponseInvalidException e) {
            log.warn("AI 응답 형식 오류 requestId={} type={} executionNo={}",
                    execution.requestId(), handler.type(), execution.executionNo());
            return failExecution(execution, handler);
        } catch (RuntimeException e) {
            failQuietly(execution, handler, e);
            throw e;
        }

        return switch (completion) {
            case AiRequestCompletion.Succeeded succeeded -> new AiRequestResult.Completed(succeeded.result());
            case AiRequestCompletion.RoomEnded roomEnded -> {
                log.info("세션 종료 후 도착한 AI 응답 미반영 requestId={} type={} executionNo={}",
                        execution.requestId(), handler.type(), execution.executionNo());
                throw new CustomException(ROOM_ENDED_DURING_REQUEST);
            }
            case AiRequestCompletion.Stale stale -> {
                log.info("이전 실행의 늦은 AI 응답 미반영 requestId={} type={} executionNo={}",
                        execution.requestId(), handler.type(), execution.executionNo());
                yield aiRequestTransactionService.currentResult(execution, handler);
            }
        };
    }

    /**
     * 최신 실행이면 FAILED로 바꾸고 502 유형별 코드를 던진다. 이미 다른 실행이 이어받았으면 현재 상태로 응답한다
     */
    private <C> AiRequestResult failExecution(AiRequestExecution<C> execution, AiRequestHandler<C> handler) {

        ErrorCode failureCode = handler.type().getFailureCode();
        if (aiRequestTransactionService.fail(execution, failureCode)) {
            throw new CustomException(failureCode, handler.failureDetail(execution.requestId(), execution.context()));
        }

        return aiRequestTransactionService.currentResult(execution, handler);
    }

    // 예상하지 못한 예외로 끝날 때 요청이 PROCESSING으로 남지 않게 정리한다. 정리 실패는 원래 예외를 가리지 않는다
    private <C> void failQuietly(AiRequestExecution<C> execution, AiRequestHandler<C> handler, RuntimeException cause) {
        log.error("AI 요청 처리 중 예외 requestId={} type={} executionNo={} exception={}{}",
                execution.requestId(), handler.type(), execution.executionNo(),
                SafeExceptionLog.causeChain(cause), SafeExceptionLog.rootCauseFrames(cause));
        try {
            aiRequestTransactionService.fail(execution, handler.type().getFailureCode());
        } catch (RuntimeException e) {
            log.error("AI 요청 실패 정리 실패 requestId={} exception={}", execution.requestId(), SafeExceptionLog.causeChain(e));
        }
    }

    private static String errorTypeOf(RuntimeException e) {
        return e instanceof LlmException llmException ? llmException.getErrorType().name() : e.getClass().getSimpleName();
    }
}
