package com.shelved.domain.hint.repository;

import com.shelved.common.entity.HintFollowup;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HintFollowupRepository extends JpaRepository<HintFollowup, Long> {

    // 이 힌트의 보충 질문 사용이 확정되었는지 (정상 답변이 저장됨)
    boolean existsByHintId(Long hintId);

    /**
     * 한 세션의 보충 질문 전부 (힌트 기록 조회에서 힌트별로 묶는다. 힌트 ID는 지연 로딩 없이 읽힌다)
     */
    @Query("select f from HintFollowup f where f.hint.room.id = :roomId")
    List<HintFollowup> findAllByRoomId(@Param("roomId") Long roomId);
}
