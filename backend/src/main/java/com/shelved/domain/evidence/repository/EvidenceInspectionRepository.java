package com.shelved.domain.evidence.repository;

import com.shelved.common.entity.EvidenceInspection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EvidenceInspectionRepository extends JpaRepository<EvidenceInspection, Long> {

    boolean existsByRoomIdAndEvidenceId(Long roomId, Long evidenceId);

    /**
     * 이 세션에서 조사한 물증 이름 (처음 조사한 순, 힌트 프롬프트용)
     */
    @Query("""
            select e.name
            from EvidenceInspection i join i.evidence e
            where i.room.id = :roomId
            order by i.firstInspectedAt asc, i.id asc
            """)
    List<String> findInspectedEvidenceNameList(@Param("roomId") Long roomId);
}
