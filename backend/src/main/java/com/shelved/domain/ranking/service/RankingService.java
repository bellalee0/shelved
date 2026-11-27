package com.shelved.domain.ranking.service;

import com.shelved.common.dto.PageResponse;
import com.shelved.common.entity.MysteryCase;
import com.shelved.domain.cases.repository.MysteryCaseRepository;
import com.shelved.domain.ranking.dto.RankingGetResponse;
import com.shelved.domain.ranking.dto.RankingResponse;
import com.shelved.domain.ranking.repository.RankingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RankingService {

    private final MysteryCaseRepository mysteryCaseRepository;
    private final RankingRepository rankingRepository;

    /**
     * 공개 사건의 랭킹. 내 순위는 요청한 페이지와 무관하게 전체 기준이다
     */
    @Transactional(readOnly = true)
    public RankingGetResponse getRanking(Long userId, Long caseId, Pageable pageable) {

        MysteryCase mysteryCase = mysteryCaseRepository.findPublishedCaseById(caseId);

        Page<RankingResponse> rankingPage = rankingRepository.findRankingPage(caseId, pageable)
                .map(RankingResponse::from);
        RankingResponse myRanking = rankingRepository.findRankingByUser(caseId, userId)
                .map(RankingResponse::from)
                .orElse(null);

        return RankingGetResponse.of(mysteryCase, myRanking, PageResponse.from(rankingPage));
    }
}
