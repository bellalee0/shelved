package com.shelved.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 세션별 물증 최초 조사 이력 (힌트 참고용, 플레이어에게 목록으로 제공하지 않음)
 */
@Entity
@Table(
        name = "evidence_inspections",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_evidence_inspections_room_id_evidence_id",
                columnNames = {"room_id", "evidence_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EvidenceInspection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evidence_id", nullable = false)
    private Evidence evidence;

    @Column(name = "first_inspected_at", nullable = false)
    private LocalDateTime firstInspectedAt;

    public EvidenceInspection(Room room, Evidence evidence, LocalDateTime firstInspectedAt) {
        this.room = room;
        this.evidence = evidence;
        this.firstInspectedAt = firstInspectedAt;
    }
}
