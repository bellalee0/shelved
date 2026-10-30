package com.shelved.common.entity;

import com.shelved.common.entity.json.GradingRubric;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "case_trick_elements",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_case_trick_elements_case_id_order_index",
                columnNames = {"case_id", "order_index"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CaseTrickElement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false)
    private MysteryCase mysteryCase;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    // 정답 보호: 플레이어 응답에 포함하지 않음
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "grading_rubric", nullable = false, columnDefinition = "jsonb")
    private GradingRubric gradingRubric;

    // 표시·판정 순서 (1부터)
    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    public CaseTrickElement(MysteryCase mysteryCase, String description, GradingRubric gradingRubric, int orderIndex) {
        this.mysteryCase = mysteryCase;
        this.description = description;
        this.gradingRubric = gradingRubric;
        this.orderIndex = orderIndex;
    }
}
