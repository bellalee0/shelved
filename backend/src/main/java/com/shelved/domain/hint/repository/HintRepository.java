package com.shelved.domain.hint.repository;

import com.shelved.common.entity.Hint;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HintRepository extends JpaRepository<Hint, Long> {

    // 사용 확정 힌트 수 (정상 답변이 저장된 행 수)
    long countByRoomId(Long roomId);
}
