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
 * 세션 종료 결과. 세션당 정확히 1건 (room_id UNIQUE)
 */
@Entity
@Table(
        name = "play_results",
        uniqueConstraints = @UniqueConstraint(name = "uk_play_results_room_id", columnNames = "room_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlayResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    // 랭킹 집계용 (rooms를 거치지 않고 사건별로 조회)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false)
    private MysteryCase mysteryCase;

    @Column(name = "cleared", nullable = false)
    private boolean cleared;

    // CLEARED: 정답 접수 시점까지, FAILED: 종료 시점까지의 누적 진행 시간, EXPIRED: 제한시간
    @Column(name = "duration_sec", nullable = false)
    private int durationSec;

    @Column(name = "hint_used_count", nullable = false)
    private int hintUsedCount;

    @Column(name = "ended_at", nullable = false)
    private LocalDateTime endedAt;

    public PlayResult(
            Room room,
            MysteryCase mysteryCase,
            boolean cleared,
            int durationSec,
            int hintUsedCount,
            LocalDateTime endedAt
    ) {
        this.room = room;
        this.mysteryCase = mysteryCase;
        this.cleared = cleared;
        this.durationSec = durationSec;
        this.hintUsedCount = hintUsedCount;
        this.endedAt = endedAt;
    }
}
