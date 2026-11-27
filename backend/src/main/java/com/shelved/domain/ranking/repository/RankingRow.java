package com.shelved.domain.ranking.repository;

import java.time.LocalDateTime;

/**
 * 랭킹 네이티브 쿼리의 한 행. 접근자 이름은 쿼리의 열 별칭과 같아야 한다
 */
public interface RankingRow {

    long getRanking();

    String getNickname();

    int getDurationSec();

    LocalDateTime getClearedAt();
}
