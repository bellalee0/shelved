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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "evidences")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Evidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false)
    private MysteryCase mysteryCase;

    @Column(name = "name", nullable = false)
    private String name;

    // 조사 API 응답으로만 반환한다 (맵·세션 조회 응답에 포함하지 않음)
    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    public Evidence(MysteryCase mysteryCase, String name, String description) {
        this.mysteryCase = mysteryCase;
        this.name = name;
        this.description = description;
    }
}
