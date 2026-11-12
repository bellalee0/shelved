package com.shelved.domain.room.repository;

import static com.shelved.common.exception.ErrorCode.AI_REQUEST_NOT_FOUND;

import com.shelved.common.entity.AiRequest;
import com.shelved.common.enums.AiRequestStatus;
import com.shelved.common.enums.AiRequestType;
import com.shelved.common.exception.CustomException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiRequestRepository extends JpaRepository<AiRequest, Long> {

    long countByRoomIdAndRequestTypeAndStatus(Long roomId, AiRequestType requestType, AiRequestStatus status);

    Optional<AiRequest> findByRoomIdAndRequestId(Long roomId, String requestId);

    List<AiRequest> findByRoomIdAndRequestTypeAndStatus(Long roomId, AiRequestType requestType, AiRequestStatus status);

    List<AiRequest> findByRoomIdOrderByIdAsc(Long roomId);

    @Query("select a.room.id from AiRequest a where a.id = :id")
    Optional<Long> findRoomIdById(@Param("id") Long id);

    /**
     * 복구 대상 후보 (주어진 상태이고 마지막 갱신이 기준 시각 이전). 잠금 없이 ID만 읽고, 요청별 정리에서 다시 확인한다
     */
    @Query("select a.id from AiRequest a where a.status = :status and a.updatedAt <= :updatedBefore order by a.id asc")
    List<Long> findIdsByStatusAndUpdatedAtLessThanEqual(
            @Param("status") AiRequestStatus status,
            @Param("updatedBefore") LocalDateTime updatedBefore
    );

    @Query("select a.id from AiRequest a where a.status = :status order by a.id asc")
    List<Long> findIdsByStatus(@Param("status") AiRequestStatus status);

    default AiRequest findAiRequest(Long roomId, String requestId) {
        return findByRoomIdAndRequestId(roomId, requestId)
                .orElseThrow(() -> new CustomException(AI_REQUEST_NOT_FOUND));
    }
}
