package com.shelved.domain.airequest.scheduler;

import com.shelved.domain.airequest.service.AiRequestRecoveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 처리 중(PROCESSING) AI 요청 복구. 로직은 AiRequestRecoveryService에 있고 여기서는 호출만 한다.
 * - 주기 점검: processing-recovery-interval마다 오래된 PROCESSING 정리 (테스트 프로필에서는 스케줄링이 꺼져 있다)
 * - 서버 시작 직후 1회: 남은 PROCESSING을 모두 정리 (서버 인스턴스 1대 전제)
 */
@Component
@RequiredArgsConstructor
public class AiRequestRecoveryScheduler {

    private final AiRequestRecoveryService aiRequestRecoveryService;

    @Scheduled(fixedDelayString = "${shelved.llm.processing-recovery-interval}")
    public void recoverStaleRequests() {
        aiRequestRecoveryService.recoverStaleRequests();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverAllProcessingRequests() {
        aiRequestRecoveryService.recoverAllProcessingRequests();
    }
}
