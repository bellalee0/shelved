package com.shelved.domain.room.repository;

import com.shelved.common.entity.PlayResult;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayResultRepository extends JpaRepository<PlayResult, Long> {

    List<PlayResult> findAllByRoomIdIn(Collection<Long> roomIds);

    boolean existsByRoomId(Long roomId);

    Optional<PlayResult> findByRoomId(Long roomId);
}
