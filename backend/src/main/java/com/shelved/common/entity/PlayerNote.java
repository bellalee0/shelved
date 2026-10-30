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
 * 개인 노트. 내용은 로그에 남기지 않는다. revision이 같을 때만 갱신한다 (backend.md 16).
 */
@Entity
@Table(
        name = "player_notes",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_player_notes_room_id_user_id",
                columnNames = {"room_id", "user_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlayerNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 원문 그대로 보존 (trim·정규화 없음)
    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "revision", nullable = false)
    private int revision;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 세션 생성 시 함께 만드는 빈 노트 (revision 0)
     */
    public PlayerNote(Room room, User user, LocalDateTime createdAt) {
        this.room = room;
        this.user = user;
        this.content = "";
        this.revision = 0;
        this.updatedAt = createdAt;
    }
}
