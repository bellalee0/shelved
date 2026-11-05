package com.shelved.domain.cases.repository;

import static com.shelved.common.exception.ErrorCode.MAP_NOT_CONFIGURED;

import com.shelved.common.entity.CaseMap;
import com.shelved.common.exception.CustomException;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaseMapRepository extends JpaRepository<CaseMap, Long> {

    Optional<CaseMap> findByMysteryCaseId(Long caseId);

    default CaseMap findCaseMapByCaseId(Long caseId) {
        return findByMysteryCaseId(caseId)
                .orElseThrow(() -> new CustomException(MAP_NOT_CONFIGURED));
    }
}
