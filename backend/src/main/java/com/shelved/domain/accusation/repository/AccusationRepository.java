package com.shelved.domain.accusation.repository;

import com.shelved.common.entity.Accusation;
import com.shelved.common.enums.GradingStatus;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 지목. 대체 여부는 AI_REQUESTS.superseded_at에만 있으므로, "미해결 지목"은 같은 세션·같은 requestId의 요청 행이 대체되지 않은 것만 센다
 */
public interface AccusationRepository extends JpaRepository<Accusation, Long> {

    // 마감 전 접수됐지만 채점이 끝나지 않은 지목 (채점 중 또는 채점 실패)
    Set<GradingStatus> UNRESOLVED_GRADING_STATUSES = EnumSet.of(GradingStatus.PENDING, GradingStatus.ERROR);

    /**
     * 여러 세션의 대체되지 않은 지목 중 주어진 채점 상태인 것 (세션 목록의 일괄 조회용). id 오름차순이라 같은 세션의 마지막 원소가 가장 최근 지목
     */
    @Query("""
            select a from Accusation a
            where a.room.id in :roomIds and a.gradingStatus in :gradingStatuses
              and not exists (
                  select 1 from AiRequest r
                  where r.room = a.room and r.requestId = a.requestId and r.supersededAt is not null)
            order by a.id asc
            """)
    List<Accusation> findAllByRoomIdInAndGradingStatusInOrderByIdAsc(
            @Param("roomIds") Collection<Long> roomIds,
            @Param("gradingStatuses") Collection<GradingStatus> gradingStatuses
    );

    /**
     * 대체되지 않은 지목 중 주어진 채점 상태인 것. 인물을 함께 읽는다. 최근 접수순
     */
    @Query("""
            select a from Accusation a join fetch a.suspect
            where a.room.id = :roomId and a.gradingStatus in :gradingStatuses
              and not exists (
                  select 1 from AiRequest r
                  where r.room = a.room and r.requestId = a.requestId and r.supersededAt is not null)
            order by a.id desc
            """)
    List<Accusation> findNotSupersededByRoomIdAndGradingStatusIn(
            @Param("roomId") Long roomId,
            @Param("gradingStatuses") Collection<GradingStatus> gradingStatuses
    );

    @Query("""
            select count(a) from Accusation a
            where a.room.id = :roomId and a.gradingStatus in :gradingStatuses
              and not exists (
                  select 1 from AiRequest r
                  where r.room = a.room and r.requestId = a.requestId and r.supersededAt is not null)
            """)
    long countNotSupersededByRoomIdAndGradingStatusIn(
            @Param("roomId") Long roomId,
            @Param("gradingStatuses") Collection<GradingStatus> gradingStatuses
    );

    /**
     * 세션의 지목 기록. 인물을 함께 읽는다. 오래된 순
     */
    @Query("select a from Accusation a join fetch a.suspect where a.room.id = :roomId order by a.id asc")
    List<Accusation> findAllWithSuspectByRoomId(@Param("roomId") Long roomId);

    Optional<Accusation> findByRoomIdAndRequestId(Long roomId, String requestId);

    long countByRoomIdAndGradingStatus(Long roomId, GradingStatus gradingStatus);

    long countByRoomIdAndGradingStatusAndIdLessThanEqual(Long roomId, GradingStatus gradingStatus, Long id);

    /**
     * 유효한 미해결 지목(채점 중·채점 실패이고 대체되지 않음)이 있는지. 있으면 시간이 소진돼도 최종 종료를 보류한다
     */
    default boolean existsUnresolvedByRoomId(Long roomId) {
        return countNotSupersededByRoomIdAndGradingStatusIn(roomId, UNRESOLVED_GRADING_STATUSES) > 0;
    }
}
