package com.shelved.domain.cases.repository;

import static com.shelved.common.exception.ErrorCode.CASE_NOT_FOUND;

import com.shelved.common.entity.MysteryCase;
import com.shelved.common.enums.CaseStatus;
import com.shelved.common.exception.CustomException;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MysteryCaseRepository extends JpaRepository<MysteryCase, Long> {

    List<MysteryCase> findAllByStatusOrderByIdAsc(CaseStatus status);

    Optional<MysteryCase> findByIdAndStatus(Long id, CaseStatus status);

    boolean existsByDisplayTitleAndStatus(String displayTitle, CaseStatus status);

    /**
     * 플레이어가 입장할 수 있는 공개 사건. 없거나 비공개면 CASE_NOT_FOUND (존재 여부를 구분하지 않는다)
     */
    default MysteryCase findPublishedCaseById(Long caseId) {
        return findByIdAndStatus(caseId, CaseStatus.PUBLISHED)
                .orElseThrow(() -> new CustomException(CASE_NOT_FOUND));
    }
}
