package com.shelved.domain.cases.repository;

import static com.shelved.common.exception.ErrorCode.SUSPECT_NOT_FOUND;

import com.shelved.common.entity.Suspect;
import com.shelved.common.exception.CustomException;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SuspectRepository extends JpaRepository<Suspect, Long> {

    List<Suspect> findAllByMysteryCaseIdOrderByIdAsc(Long caseId);

    Optional<Suspect> findByIdAndMysteryCaseId(Long suspectId, Long caseId);

    /**
     * 주어진 사건의 인물. 없거나 다른 사건의 인물이면 404 SUSPECT_NOT_FOUND
     */
    default Suspect findSuspectOfCase(Long suspectId, Long caseId) {
        return findByIdAndMysteryCaseId(suspectId, caseId)
                .orElseThrow(() -> new CustomException(SUSPECT_NOT_FOUND));
    }
}
