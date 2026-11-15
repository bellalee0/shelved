package com.shelved.domain.airequest.service;

import com.shelved.common.config.LlmProperties;
import com.shelved.common.enums.AiRequestStatus;
import com.shelved.common.utils.SafeExceptionLog;
import com.shelved.domain.room.repository.AiRequestRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 남은 PROCESSING 요청 복구 (backend.md AI 연동 규칙). 후보 ID를 잠금 없이 읽고, 요청마다 짧은 트랜잭션에서 세션 잠금 → 재확인 → 정리한다.
 * 정리된 요청에 이전 실행의 응답이 늦게 도착해도 PROCESSING이 아니므로 반영되지 않는다. 한 요청이 실패해도 나머지를 계속 처리한다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiRequestRecoveryService {

    private final AiRequestRepository aiRequestRepository;
    private final AiRequestTransactionService aiRequestTransactionService;
    private final LlmProperties llmProperties;
    private final Clock clock;

    /**
     * 작업 시간 제한: 마지막 갱신 후 game-timeout + processing-recovery-margin이 지난 PROCESSING을 유형별 실패 코드로 정리한다
     *
     * @return 정리한 요청 수
     */
    public int recoverStaleRequests() {

        LocalDateTime updatedBefore = LocalDateTime.now(clock).minus(llmProperties.gameProcessingRecoveryAfter());
        List<Long> aiRequestIdList = aiRequestRepository.findIdsByStatusAndUpdatedAtLessThanEqual(
                AiRequestStatus.PROCESSING, updatedBefore);

        return failAll(aiRequestIdList, updatedBefore);
    }

    /**
     * 서버 시작 시 복구: 남은 PROCESSING을 시각과 관계없이 모두 정리한다.
     * MVP는 서버 인스턴스 1대 전제이므로 시작 시점의 PROCESSING은 모두 이전 프로세스에서 끝나지 못한 요청이다
     *
     * @return 정리한 요청 수
     */
    public int recoverAllProcessingRequests() {

        List<Long> aiRequestIdList = aiRequestRepository.findIdsByStatus(AiRequestStatus.PROCESSING);

        return failAll(aiRequestIdList, null);
    }

    private int failAll(List<Long> aiRequestIdList, LocalDateTime updatedBefore) {
        int recoveredCount = 0;
        for (Long aiRequestId : aiRequestIdList) {
            try {
                if (aiRequestTransactionService.failProcessing(aiRequestId, updatedBefore)) {
                    recoveredCount++;
                }
            } catch (RuntimeException e) {
                log.error("AI 요청 복구 실패 aiRequestId={} exception={}{}",
                        aiRequestId, SafeExceptionLog.causeChain(e), SafeExceptionLog.rootCauseFrames(e));
            }
        }
        if (recoveredCount > 0) {
            log.info("처리 중 AI 요청 복구 count={}", recoveredCount);
        }
        return recoveredCount;
    }
}
