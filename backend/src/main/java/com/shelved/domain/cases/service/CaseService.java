package com.shelved.domain.cases.service;

import com.shelved.common.config.GameProperties;
import com.shelved.common.enums.CaseStatus;
import com.shelved.domain.cases.dto.CaseCardResponse;
import com.shelved.domain.cases.repository.MysteryCaseRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CaseService {

    private final MysteryCaseRepository mysteryCaseRepository;
    private final GameProperties gameProperties;

    /**
     * 공개(PUBLISHED) 사건 카드 전체. MVP는 사건 수가 적어 페이지네이션 없이 ID 순으로 반환한다
     */
    @Transactional(readOnly = true)
    public List<CaseCardResponse> getCaseList() {
        return mysteryCaseRepository.findAllByStatusOrderByIdAsc(CaseStatus.PUBLISHED).stream()
                .map(mysteryCase -> CaseCardResponse.from(mysteryCase, gameProperties))
                .toList();
    }
}
