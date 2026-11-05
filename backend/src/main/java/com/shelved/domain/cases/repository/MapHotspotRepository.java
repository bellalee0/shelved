package com.shelved.domain.cases.repository;

import com.shelved.common.entity.MapHotspot;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MapHotspotRepository extends JpaRepository<MapHotspot, Long> {

    List<MapHotspot> findAllByMysteryCaseId(Long caseId);
}
