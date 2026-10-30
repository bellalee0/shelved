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
@Table(name = "suspects")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Suspect {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false)
    private MysteryCase mysteryCase;

    @Column(name = "name", nullable = false)
    private String name;

    // NPC 내부 설정 (정답 보호). 대화 불가 인물은 null
    @Column(name = "persona_prompt", columnDefinition = "text")
    private String personaPrompt;

    // 맵에 배치하지 않은 인물은 null
    @Column(name = "sprite_key")
    private String spriteKey;

    // 정답 보호: 리빌 전 플레이어 응답에 포함하지 않음
    @Column(name = "is_culprit", nullable = false)
    private boolean culprit;

    // false여도 지목 대상에는 포함
    @Column(name = "can_converse", nullable = false)
    private boolean canConverse;

    public Suspect(
            MysteryCase mysteryCase,
            String name,
            String personaPrompt,
            String spriteKey,
            boolean culprit,
            boolean canConverse
    ) {
        this.mysteryCase = mysteryCase;
        this.name = name;
        this.personaPrompt = personaPrompt;
        this.spriteKey = spriteKey;
        this.culprit = culprit;
        this.canConverse = canConverse;
    }
}
