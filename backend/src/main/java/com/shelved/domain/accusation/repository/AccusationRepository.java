package com.shelved.domain.accusation.repository;

import com.shelved.common.entity.Accusation;
import com.shelved.common.enums.GradingStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccusationRepository extends JpaRepository<Accusation, Long> {

    /**
     * 여러 세션의 지목 중 주어진 채점 상태인 것 (세션 목록의 일괄 조회용). id 오름차순이라 같은 세션의 마지막 원소가 가장 최근 지목
     */
    @Query("""
            select a from Accusation a
            where a.room.id in :roomIds and a.gradingStatus in :gradingStatuses
            order by a.id asc
            """)
    List<Accusation> findAllByRoomIdInAndGradingStatusInOrderByIdAsc(
            @Param("roomIds") Collection<Long> roomIds,
            @Param("gradingStatuses") Collection<GradingStatus> gradingStatuses
    );

    boolean existsByRoomIdAndGradingStatusIn(Long roomId, Collection<GradingStatus> gradingStatuses);

    long countByRoomIdAndGradingStatus(Long roomId, GradingStatus gradingStatus);
}
