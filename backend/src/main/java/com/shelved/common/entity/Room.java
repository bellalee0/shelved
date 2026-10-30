package com.shelved.common.entity;

import com.shelved.common.enums.RoomStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 플레이 세션. 시간은 서버 기준으로만 계산한다 (backend.md 12).
 * 상태 전이·시간 정산은 세션 행을 비관적 잠금으로 조회한 뒤 처리한다 (backend.md 15).
 */
@Entity
@Table(name = "rooms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false)
    private MysteryCase mysteryCase;

    // 관리자 테스트 세션. 랭킹·일반 세션 목록에서 제외
    @Column(name = "is_test", nullable = false)
    private boolean test;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "host_user_id", nullable = false)
    private User hostUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RoomStatus status;

    // MVP 미사용 (솔로 전용)
    @Column(name = "invite_code")
    private String inviteCode;

    // 세션 생성 시 난이도별 설정값으로 정한다
    @Column(name = "time_limit_sec", nullable = false)
    private int timeLimitSec;

    // 최초 "수사 시작" 시각
    @Column(name = "started_at")
    private LocalDateTime startedAt;

    // 일시정지를 제외한 누적 진행 시간. active_since 이후 구간은 아직 더하지 않은 값
    @Column(name = "elapsed_active_ms", nullable = false)
    private long elapsedActiveMs;

    // 현재 진행 구간의 시작 시각. IN_PROGRESS가 아니면 null
    @Column(name = "active_since")
    private LocalDateTime activeSince;

    // 마지막 하트비트 수신 시각 (이탈 판정 기준)
    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    // 현재 조작 연결 식별자 (UUID). start·resume마다 새로 발급
    @Column(name = "connection_id")
    private String connectionId;

    /**
     * READY 세션 생성
     */
    public Room(MysteryCase mysteryCase, User hostUser, boolean test, int timeLimitSec, LocalDateTime createdAt) {
        this.mysteryCase = mysteryCase;
        this.hostUser = hostUser;
        this.test = test;
        this.timeLimitSec = timeLimitSec;
        this.createdAt = createdAt;
        this.status = RoomStatus.READY;
        this.elapsedActiveMs = 0L;
    }
}
