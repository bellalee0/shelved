package com.shelved.domain.hint.repository;

import static com.shelved.common.exception.ErrorCode.HINT_NOT_FOUND;

import com.shelved.common.entity.Hint;
import com.shelved.common.exception.CustomException;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HintRepository extends JpaRepository<Hint, Long> {

    // 사용 확정 힌트 수 (정상 답변이 저장된 행 수)
    long countByRoomId(Long roomId);

    /**
     * 힌트 기록 (오래된 순)
     */
    List<Hint> findByRoomIdOrderByCreatedAtAscIdAsc(Long roomId);

    /**
     * 최근 힌트부터 limit개 (힌트 프롬프트의 이전 힌트용)
     */
    List<Hint> findByRoomIdOrderByCreatedAtDescIdDesc(Long roomId, Limit limit);

    Optional<Hint> findByIdAndRoomId(Long hintId, Long roomId);

    /**
     * 이 세션의 힌트. 없거나 다른 세션의 힌트면 404 HINT_NOT_FOUND
     */
    default Hint findHintOfRoom(Long hintId, Long roomId) {
        return findByIdAndRoomId(hintId, roomId)
                .orElseThrow(() -> new CustomException(HINT_NOT_FOUND));
    }
}
