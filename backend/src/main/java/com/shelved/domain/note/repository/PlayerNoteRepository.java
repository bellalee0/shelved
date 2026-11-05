package com.shelved.domain.note.repository;

import com.shelved.common.entity.PlayerNote;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerNoteRepository extends JpaRepository<PlayerNote, Long> {
}
