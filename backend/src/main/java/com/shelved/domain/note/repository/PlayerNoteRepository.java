package com.shelved.domain.note.repository;

import com.shelved.common.entity.PlayerNote;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerNoteRepository extends JpaRepository<PlayerNote, Long> {

    Optional<PlayerNote> findByRoomIdAndUserId(Long roomId, Long userId);

    /**
     * 세션 생성 시 항상 함께 만들어지므로, 없으면 데이터 오류로 보고 500으로 처리한다 (조용히 새로 만들지 않음)
     */
    default PlayerNote findPlayerNote(Long roomId, Long userId) {
        return findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new IllegalStateException("세션의 개인 노트가 없습니다."));
    }
}
