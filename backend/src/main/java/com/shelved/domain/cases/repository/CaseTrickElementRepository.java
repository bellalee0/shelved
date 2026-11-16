package com.shelved.domain.cases.repository;

import com.shelved.common.entity.CaseTrickElement;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaseTrickElementRepository extends JpaRepository<CaseTrickElement, Long> {

    List<CaseTrickElement> findAllByMysteryCaseIdOrderByOrderIndexAsc(Long caseId);
}
