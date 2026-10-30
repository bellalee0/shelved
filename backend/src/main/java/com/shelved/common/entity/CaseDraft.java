package com.shelved.common.entity;

import com.shelved.common.entity.json.Draft;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "case_drafts",
        uniqueConstraints = @UniqueConstraint(name = "uk_case_drafts_case_id", columnNames = "case_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CaseDraft {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false)
    private MysteryCase mysteryCase;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "draft_json", nullable = false, columnDefinition = "jsonb")
    private Draft draftJson;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public CaseDraft(MysteryCase mysteryCase, Draft draftJson, LocalDateTime updatedAt) {
        this.mysteryCase = mysteryCase;
        this.draftJson = draftJson;
        this.updatedAt = updatedAt;
    }

    /**
     * 새 사건의 빈 초안
     */
    public static CaseDraft empty(MysteryCase mysteryCase, LocalDateTime createdAt) {
        return new CaseDraft(mysteryCase, Draft.empty(), createdAt);
    }
}
