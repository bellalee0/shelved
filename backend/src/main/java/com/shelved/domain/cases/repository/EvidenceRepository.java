package com.shelved.domain.cases.repository;

import static com.shelved.common.exception.ErrorCode.EVIDENCE_NOT_FOUND;

import com.shelved.common.entity.Evidence;
import com.shelved.common.exception.CustomException;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvidenceRepository extends JpaRepository<Evidence, Long> {

    List<Evidence> findAllByMysteryCaseIdOrderByIdAsc(Long caseId);

    Optional<Evidence> findByIdAndMysteryCaseId(Long evidenceId, Long caseId);

    /**
     * 주어진 사건의 물증. 없거나 다른 사건의 물증이면 404 EVIDENCE_NOT_FOUND
     */
    default Evidence findEvidenceOfCase(Long evidenceId, Long caseId) {
        return findByIdAndMysteryCaseId(evidenceId, caseId)
                .orElseThrow(() -> new CustomException(EVIDENCE_NOT_FOUND));
    }
}
