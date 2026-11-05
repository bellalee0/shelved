package com.shelved.domain.room.repository;

import static com.shelved.common.exception.ErrorCode.ROOM_NOT_FOUND;

import com.shelved.common.entity.Room;
import com.shelved.common.enums.RoomStatus;
import com.shelved.common.exception.CustomException;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomRepository extends JpaRepository<Room, Long> {

    // 사건을 함께 조회 (세션 하위 API 대부분이 사건 정보를 쓴다)
    @Query("select r from Room r join fetch r.mysteryCase where r.id = :roomId")
    Optional<Room> findWithCaseById(@Param("roomId") Long roomId);

    // 상태 변경용 비관적 잠금 조회 (backend.md 15). 잠금은 rooms 행에만 건다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.id = :roomId")
    Optional<Room> findWithLockById(@Param("roomId") Long roomId);

    /**
     * 사용자가 참가한 같은 사건의 일반 세션 중 주어진 상태인 것 (가장 최근 1건)
     */
    @Query("""
            select r.id from Room r
            where r.mysteryCase.id = :caseId
              and r.test = false
              and r.status in :statuses
              and exists (select 1 from RoomParticipant p where p.room = r and p.user.id = :userId)
            order by r.id desc
            limit 1
            """)
    Optional<Long> findLatestRoomIdByUserAndCase(
            @Param("userId") Long userId,
            @Param("caseId") Long caseId,
            @Param("statuses") Collection<RoomStatus> statuses
    );

    /**
     * 내 세션 목록. 테스트 세션 제외, 최신 생성순. 사건은 fetch join으로 함께 읽는다
     */
    @Query(
            value = """
                    select r from Room r join fetch r.mysteryCase
                    where r.test = false
                      and r.status in :statuses
                      and exists (select 1 from RoomParticipant p where p.room = r and p.user.id = :userId)
                    order by r.createdAt desc, r.id desc
                    """,
            countQuery = """
                    select count(r) from Room r
                    where r.test = false
                      and r.status in :statuses
                      and exists (select 1 from RoomParticipant p where p.room = r and p.user.id = :userId)
                    """
    )
    Page<Room> findPageByParticipant(
            @Param("userId") Long userId,
            @Param("statuses") Collection<RoomStatus> statuses,
            Pageable pageable
    );

    /**
     * 이탈 감지 대상 후보 (주어진 상태이고 마지막 접속 확인이 기준 시각 이전). 잠금 없이 ID만 읽고, 세션별 정산에서 다시 확인한다
     */
    @Query("select r.id from Room r where r.status = :status and r.lastSeenAt <= :seenBefore order by r.id asc")
    List<Long> findIdsByStatusAndLastSeenAtLessThanEqual(
            @Param("status") RoomStatus status,
            @Param("seenBefore") LocalDateTime seenBefore
    );

    default Room findRoomWithCaseById(Long roomId) {
        return findWithCaseById(roomId)
                .orElseThrow(() -> new CustomException(ROOM_NOT_FOUND));
    }

    default Room findRoomWithLockById(Long roomId) {
        return findWithLockById(roomId)
                .orElseThrow(() -> new CustomException(ROOM_NOT_FOUND));
    }
}
