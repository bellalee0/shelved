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
 * 정상 답변이 저장된 새 힌트. 행 수가 힌트 사용 횟수다.
 */
@Entity
@Table(
        name = "hints",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_hints_room_id_request_id",
                columnNames = {"room_id", "request_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Hint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

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

    public Hint(
            Room room,
            User requestedByUser,
            String requestId,
            String question,
            String content,
            LocalDateTime createdAt
    ) {
        this.room = room;
        this.requestedByUser = requestedByUser;
        this.requestId = requestId;
        this.question = question;
        this.content = content;
        this.createdAt = createdAt;
    }
}
