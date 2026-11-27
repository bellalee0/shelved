package com.shelved.domain.ranking.dto;

import com.shelved.common.dto.PageResponse;
import com.shelved.common.entity.MysteryCase;

/**
 * 사건별 랭킹 (docs/api/cases-ranking.md GET rankings)
 *
 * @param myRanking   요청자의 최고 기록과 전체 기준 순위. 성공 기록이 없으면 null
 * @param rankingPage 순위순 목록
 */
public record RankingGetResponse(
        Long caseId,
        String displayTitle,
        RankingResponse myRanking,
        PageResponse<RankingResponse> rankingPage
) {

    public static RankingGetResponse of(
            MysteryCase mysteryCase,
            RankingResponse myRanking,
            PageResponse<RankingResponse> rankingPage
    ) {
        return new RankingGetResponse(mysteryCase.getId(), mysteryCase.getDisplayTitle(), myRanking, rankingPage);
    }
}
