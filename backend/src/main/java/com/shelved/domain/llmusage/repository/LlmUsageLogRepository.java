package com.shelved.domain.llmusage.repository;

import com.shelved.common.entity.LlmUsageLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LlmUsageLogRepository extends JpaRepository<LlmUsageLog, Long> {
}
