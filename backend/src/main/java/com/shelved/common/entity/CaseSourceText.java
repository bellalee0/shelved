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
 * 원작 원문 전문 (관리자 전용). 로그와 API 응답에 내용을 남기지 않는다.
 */
@Entity
@Table(
        name = "case_source_texts",
        uniqueConstraints = @UniqueConstraint(name = "uk_case_source_texts_case_id", columnNames = "case_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CaseSourceText {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false)
    private MysteryCase mysteryCase;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    public CaseSourceText(MysteryCase mysteryCase, String originalFilename, String content, LocalDateTime uploadedAt) {
        this.mysteryCase = mysteryCase;
        this.originalFilename = originalFilename;
        this.content = content;
        this.uploadedAt = uploadedAt;
    }
}
