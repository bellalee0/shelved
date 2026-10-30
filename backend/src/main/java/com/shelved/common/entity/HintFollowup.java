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
 * 힌트당 1회의 무료 보충 질문
 */
@Entity
@Table(
        name = "hint_followups",
        uniqueConstraints = @UniqueConstraint(name = "uk_hint_followups_hint_id", columnNames = "hint_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HintFollowup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hint_id", nullable = false)
    private Hint hint;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by_user_id", nullable = false)
    private User requestedByUser;

    @Column(name = "request_id", nullable = false)
    private String requestId;

    @Column(name = "question", nullable = false, columnDefinition = "text")
    private String question;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public HintFollowup(
            Hint hint,
            User requestedByUser,
            String requestId,
            String question,
            String content,
            LocalDateTime createdAt
    ) {
        this.hint = hint;
        this.requestedByUser = requestedByUser;
        this.requestId = requestId;
        this.question = question;
        this.content = content;
        this.createdAt = createdAt;
    }
}
