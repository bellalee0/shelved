package com.shelved.domain.evidence.repository;

import com.shelved.common.entity.EvidenceInspection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvidenceInspectionRepository extends JpaRepository<EvidenceInspection, Long> {

    boolean existsByRoomIdAndEvidenceId(Long roomId, Long evidenceId);
}
