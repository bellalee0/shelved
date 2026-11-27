package com.shelved.domain.ranking.dto;

import com.shelved.domain.ranking.repository.RankingRow;
import java.time.LocalDateTime;

/**
 * 랭킹 1건 (docs/api/cases-ranking.md RankingResponse). 사용자 ID는 넣지 않는다
 *
 * @param rank      공동 순위 (1·2·2·4)
 * @param clearedAt 정답 제출 접수 시각
 */
public record RankingResponse(
        long rank,
        String nickname,
        int durationSec,
        LocalDateTime clearedAt
) {

    public static RankingResponse from(RankingRow row) {
        return new RankingResponse(
                row.getRanking(),
                row.getNickname(),
                row.getDurationSec(),
                row.getClearedAt()
        );
    }
}
