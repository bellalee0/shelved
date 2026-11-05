package com.shelved.domain.room.repository;

import com.shelved.common.entity.RoomParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomParticipantRepository extends JpaRepository<RoomParticipant, Long> {

    boolean existsByRoomIdAndUserId(Long roomId, Long userId);
}
