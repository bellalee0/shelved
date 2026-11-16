package com.shelved.domain.npc.repository;

import com.shelved.common.entity.NpcConversation;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NpcConversationRepository extends JpaRepository<NpcConversation, Long> {

    Optional<NpcConversation> findByRoomIdAndSuspectId(Long roomId, Long suspectId);
}
