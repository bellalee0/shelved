package com.shelved.domain.llmusage.service;

import com.shelved.common.entity.LlmUsageLog;
import com.shelved.common.entity.MysteryCase;
import com.shelved.common.entity.Room;
import com.shelved.domain.cases.repository.MysteryCaseRepository;
import com.shelved.domain.llmusage.repository.LlmUsageLogRepository;
import com.shelved.domain.room.repository.RoomRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * LLM_USAGE_LOGS 기록 (backend.md AI 연동 규칙). AI 요청 트랜잭션과 분리된 짧은 새 트랜잭션에서 저장한다.
 * 비용(estimated_cost) 계산은 아직 하지 않으며 null로 둔다
 */
@Service
@RequiredArgsConstructor
public class LlmUsageLogService {

    private final LlmUsageLogRepository llmUsageLogRepository;
    private final RoomRepository roomRepository;
    private final MysteryCaseRepository mysteryCaseRepository;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordUsage(LlmUsageRecord usageRecord) {

        Room room = usageRecord.roomId() == null ? null : roomRepository.getReferenceById(usageRecord.roomId());
        MysteryCase mysteryCase = usageRecord.caseId() == null ? null : mysteryCaseRepository.getReferenceById(usageRecord.caseId());

        llmUsageLogRepository.save(new LlmUsageLog(
                usageRecord.requestType(),
                usageRecord.model(),
                room,
                mysteryCase,
                usageRecord.inputTokens(),
                usageRecord.outputTokens(),
                null,
                usageRecord.callStatus(),
                usageRecord.errorCode(),
                LocalDateTime.now(clock)
        ));
    }
}
