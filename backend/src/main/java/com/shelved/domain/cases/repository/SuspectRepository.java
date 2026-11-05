package com.shelved.domain.cases.repository;

import com.shelved.common.entity.Suspect;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SuspectRepository extends JpaRepository<Suspect, Long> {

    List<Suspect> findAllByMysteryCaseIdOrderByIdAsc(Long caseId);
}
