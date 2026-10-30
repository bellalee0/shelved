package com.shelved.common.entity;

import com.shelved.common.enums.HotspotType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "map_hotspots",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_map_hotspots_case_id_type_ref_id",
                columnNames = {"case_id", "type", "ref_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MapHotspot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false)
    private MysteryCase mysteryCase;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private HotspotType type;

    // 맵 좌상단 원점의 월드 픽셀 좌표
    @Column(name = "x", nullable = false)
    private int x;

    @Column(name = "y", nullable = false)
    private int y;

    // type에 따라 suspects 또는 evidences의 id를 가리키는 폴리모픽 참조 (FK 아님). 대상 검증은 저장하는 쪽에서 한다
    @Column(name = "ref_id", nullable = false)
    private Long refId;

    public MapHotspot(MysteryCase mysteryCase, HotspotType type, Long refId, int x, int y) {
        this.mysteryCase = mysteryCase;
        this.type = type;
        this.refId = refId;
        this.x = x;
        this.y = y;
    }
}
