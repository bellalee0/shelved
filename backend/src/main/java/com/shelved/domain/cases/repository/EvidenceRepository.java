package com.shelved.domain.cases.repository;

import com.shelved.common.entity.Evidence;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvidenceRepository extends JpaRepository<Evidence, Long> {

    List<Evidence> findAllByMysteryCaseIdOrderByIdAsc(Long caseId);
}
