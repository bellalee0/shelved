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

    // --- 상태 전이. 호출 전에 세션 행을 잠그고 허용 여부를 검사한다 (domain/room/service/RoomTimeSettler) ---

    /**
     * READY → IN_PROGRESS. 최초 시작 시각·진행 구간·접속 확인 시각을 모두 now로 맞춘다
     */
    public void start(LocalDateTime now, String newConnectionId) {
        this.status = RoomStatus.IN_PROGRESS;
        this.startedAt = now;
        this.activeSince = now;
        this.lastSeenAt = now;
        this.connectionId = newConnectionId;
    }

    /**
     * PAUSED → IN_PROGRESS. 새 연결을 발급해 이전 연결을 무효화한다
     */
    public void resume(LocalDateTime now, String newConnectionId) {
        this.status = RoomStatus.IN_PROGRESS;
        this.activeSince = now;
        this.lastSeenAt = now;
        this.connectionId = newConnectionId;
    }

    /**
     * 접속 확인 (하트비트·유효한 플레이 요청)
     */
    public void touch(LocalDateTime now) {
        this.lastSeenAt = now;
    }

    /**
     * IN_PROGRESS → PAUSED. 정산한 누적 시간으로 확정하고 연결을 무효화한다.
     * 시간 소진 후 채점 대기로 보류할 때도 쓴다 (settledElapsedMs = 제한시간)
     */
    public void pause(long settledElapsedMs) {
        this.status = RoomStatus.PAUSED;
        closeActiveSegment(settledElapsedMs);
    }

    /**
     * 시간 만료 종료. 누적 시간은 제한시간으로 고정한다
     */
    public void expire() {
        this.status = RoomStatus.EXPIRED;
        closeActiveSegment(timeLimitMs());
    }

    public long timeLimitMs() {
        return timeLimitSec * 1000L;
    }

    public boolean isCurrentConnection(String requestConnectionId) {
        return connectionId != null && connectionId.equals(requestConnectionId);
    }

    private void closeActiveSegment(long settledElapsedMs) {
        this.elapsedActiveMs = settledElapsedMs;
        this.activeSince = null;
        this.connectionId = null;
    }
}
