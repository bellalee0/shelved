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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "case_maps",
        uniqueConstraints = @UniqueConstraint(name = "uk_case_maps_case_id", columnNames = "case_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CaseMap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false)
    private MysteryCase mysteryCase;

    // 정적 에셋의 Tiled JSON 경로 (/assets/maps/...)
    @Column(name = "map_url", nullable = false)
    private String mapUrl;

    @Column(name = "spawn_x", nullable = false)
    private int spawnX;

    @Column(name = "spawn_y", nullable = false)
    private int spawnY;

    @Column(name = "collision_layer", nullable = false)
    private String collisionLayer;

    @Column(name = "interaction_radius_px", nullable = false)
    private int interactionRadiusPx;

    public CaseMap(
            MysteryCase mysteryCase,
            String mapUrl,
            int spawnX,
            int spawnY,
            String collisionLayer,
            int interactionRadiusPx
    ) {
        this.mysteryCase = mysteryCase;
        this.mapUrl = mapUrl;
        this.spawnX = spawnX;
        this.spawnY = spawnY;
        this.collisionLayer = collisionLayer;
        this.interactionRadiusPx = interactionRadiusPx;
    }
}
