package com.shelved.domain.cases.dto;

import com.shelved.common.config.GameProperties;
import com.shelved.common.entity.MysteryCase;
import com.shelved.common.enums.Difficulty;
import java.util.List;

/**
 * 사건 카드 (docs/api/cases-ranking.md). 원작·범인·트릭 등 정답 정보는 포함하지 않는다
 */
public record CaseCardResponse(
        Long caseId,
        String displayTitle,
        Difficulty difficulty,
        List<String> keywordTags,
        String coverImageUrl,
        long timeLimitSec,
        int hintLimit,
        int accusationLimit
) {

    public static CaseCardResponse from(MysteryCase mysteryCase, GameProperties gameProperties) {
        Difficulty difficulty = mysteryCase.getDifficulty();

        return new CaseCardResponse(
                mysteryCase.getId(),
                mysteryCase.getDisplayTitle(),
                difficulty,
                mysteryCase.getKeywordTags() == null ? List.of() : mysteryCase.getKeywordTags(),
                mysteryCase.getCoverImageUrl(),
                gameProperties.timeLimitOf(difficulty).toSeconds(),
                gameProperties.hintLimitOf(difficulty),
                gameProperties.accusationLimit()
        );
    }
}
