package com.shelved.domain.ranking.repository;

import com.shelved.common.entity.PlayResult;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * 사건별 랭킹 집계 (기획서 3.10절). 순위 계산은 PostgreSQL 창 함수로 DB에서 한다.
 * 목록·전체 개수·내 순위는 모두 RANKED_CTE의 ranked 하나를 읽어 같은 순위 정의를 쓴다
 */
public interface RankingRepository extends Repository<PlayResult, Long> {

    /**
     * ranked(user_id, duration_sec, cleared_at, ranking): 사용자당 최고 기록 1행과 사건 전체 기준 공동 순위.
     * <ul>
     *     <li>record: 테스트 세션이 아닌 성공 기록. 세션당 1행.
     *     cleared_at은 정답 지목의 접수 시각이며 세션당 값 하나만 뽑는 하위 쿼리라 정답 지목이 여러 건이어도 행이 늘지 않는다(가장 이른 값).
     *     정답 지목이 없는 것은 데이터 오류지만 기록을 누락하지 않도록 그때만 종료 시각으로 대신한다</li>
     *     <li>personal_best: 사용자별로 시간이 짧은 것, 같으면 먼저 달성한 것</li>
     *     <li>ranked: 시간이 같으면 공동 순위, 다음 순위는 인원만큼 건너뜀</li>
     * </ul>
     */
    String RANKED_CTE = """
            with record as (
                select r.host_user_id as user_id,
                       p.room_id,
                       p.duration_sec,
                       coalesce(
                           (select min(a.submitted_at)
                              from accusations a
                             where a.room_id = p.room_id
                               and a.grading_status = 'COMPLETED'
                               and a.verdict = 'CORRECT'),
                           p.ended_at) as cleared_at
                  from play_results p
                  join rooms r on r.id = p.room_id
                 where p.case_id = :caseId
                   and p.cleared = true
                   and r.is_test = false
            ),
            personal_best as (
                select user_id, duration_sec, cleared_at,
                       row_number() over (partition by user_id order by duration_sec, cleared_at, room_id) as best_order
                  from record
            ),
            ranked as (
                select user_id, duration_sec, cleared_at,
                       rank() over (order by duration_sec) as ranking
                  from personal_best
                 where best_order = 1
            )
            """;

    String SELECT_ROW = """
            select ranked.ranking as "ranking",
                   u.nickname as "nickname",
                   ranked.duration_sec as "durationSec",
                   ranked.cleared_at as "clearedAt"
              from ranked
              join users u on u.id = ranked.user_id
            """;

    /**
     * 순위순 목록. 같은 순위 안에서는 먼저 달성한 기록이 앞이고, 그것도 같으면 사용자 ID 순(페이지 경계 고정).
     * 정렬은 쿼리에 고정되어 있으므로 pageable에는 정렬을 담지 않는다
     */
    @Query(
            value = RANKED_CTE + SELECT_ROW + "order by ranked.ranking, ranked.cleared_at, ranked.user_id",
            countQuery = RANKED_CTE + "select count(*) from ranked",
            nativeQuery = true
    )
    Page<RankingRow> findRankingPage(@Param("caseId") Long caseId, Pageable pageable);

    /**
     * 사용자의 최고 기록과 전체 기준 순위. 성공 기록이 없으면 빈 값
     */
    @Query(value = RANKED_CTE + SELECT_ROW + "where ranked.user_id = :userId", nativeQuery = true)
    Optional<RankingRow> findRankingByUser(@Param("caseId") Long caseId, @Param("userId") Long userId);
}
