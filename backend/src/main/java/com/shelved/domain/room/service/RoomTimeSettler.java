package com.shelved.domain.room.service;

import com.shelved.common.config.GameProperties;
import com.shelved.common.entity.PlayResult;
import com.shelved.common.entity.Room;
import com.shelved.common.enums.GradingStatus;
import com.shelved.common.enums.RoomStatus;
import com.shelved.domain.accusation.repository.AccusationRepository;
import com.shelved.domain.hint.repository.HintRepository;
import com.shelved.domain.room.dto.RoomStatusFilter;
import com.shelved.domain.room.dto.RoomTimerResponse;
import com.shelved.domain.room.repository.PlayResultRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 세션 시간 정산 (기획서 3.5절, docs/api/room.md). 세션의 시간·상태를 바꾸는 규칙은 모두 여기에 둔다.
 * 호출하는 쪽은 rooms 행을 PESSIMISTIC_WRITE로 잠근 트랜잭션 안에서 호출한다(MANDATORY). now는 주입받은 Clock으로 구한 값이다.
 *
 * 1. 이탈 확정: now - lastSeen >= heartbeatTimeout이면 lastSeen까지만 누적. 그 시점에 이미 소진이면 만료, 아니면 PAUSED
 * 2. 유예 구간: 상태 불변. 시간이 소진돼도 일반 조회·주기 점검은 만료를 확정하지 않는다(보류)
 * 3. 접속 확인(하트비트·유효한 플레이 요청): lastSeen = now, 소진이면 만료
 * 4. 명시적 pause: lastSeen 갱신 없이 1을 먼저 적용, 아니면 now까지 누적 → 소진이면 만료, 아니면 PAUSED
 * 5. 만료: 미해결 지목이 있으면 제한시간으로 고정한 PAUSED(채점 대기), 없으면 EXPIRED + PlayResult 1건
 * PAUSED·READY·종료 상태에서는 시간이 흐르지 않으며 여기서 바꾸지 않는다.
 */
@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class RoomTimeSettler {

    // 마감 전 접수됐지만 채점이 끝나지 않은 지목
    private static final Set<GradingStatus> UNRESOLVED_GRADING_STATUSES = EnumSet.of(GradingStatus.PENDING, GradingStatus.ERROR);

    private final AccusationRepository accusationRepository;
    private final HintRepository hintRepository;
    private final PlayResultRepository playResultRepository;
    private final GameProperties gameProperties;

    /**
     * 1·2. 접속 확인이 아닌 요청(상태 조회·일시정지·이어하기·주기 점검)과 플레이 요청 모두 먼저 호출한다
     */
    public void settle(Room room, LocalDateTime now) {
        if (room.getStatus() != RoomStatus.IN_PROGRESS) {
            return;
        }

        LocalDateTime lastSeen = lastSeenOf(room);
        if (Duration.between(lastSeen, now).compareTo(gameProperties.heartbeatTimeout()) < 0) {
            return;
        }

        long elapsedAtLastSeen = elapsedAt(room, lastSeen);
        if (elapsedAtLastSeen >= room.timeLimitMs()) {
            expire(room, now);
            return;
        }
        room.pause(elapsedAtLastSeen);
    }

    /**
     * 3. 접속 확인. settle 뒤 IN_PROGRESS이고 현재 연결인 경우에만 호출한다
     */
    public void confirmPresence(Room room, LocalDateTime now) {
        room.touch(now);
        if (isTimeUp(room, now)) {
            expire(room, now);
        }
    }

    /**
     * 4. 명시적 일시정지. IN_PROGRESS가 아니면 아무것도 바꾸지 않는다. 어느 경우든 IN_PROGRESS였다면 연결이 무효화된다
     */
    public void pause(Room room, LocalDateTime now) {
        settle(room, now);
        if (room.getStatus() != RoomStatus.IN_PROGRESS) {
            return;
        }

        if (isTimeUp(room, now)) {
            expire(room, now);
            return;
        }
        room.pause(elapsedAt(room, now));
    }

    /**
     * 5. 시간 만료 처리. 소진이 확인된 IN_PROGRESS 세션에만 호출한다
     */
    public void expire(Room room, LocalDateTime now) {
        if (hasUnresolvedAccusation(room)) {
            // 마감 전 접수 지목의 채점이 끝날 때까지 최종 종료를 보류한다 (W4 채점 확정에서 종료)
            room.pause(room.timeLimitMs());
            return;
        }

        room.expire();
        // TODO(W3): 이 세션의 처리 중(PROCESSING) AI 요청을 무효화해 늦은 응답이 게임 데이터·횟수에 반영되지 않게 한다
        if (!playResultRepository.existsByRoomId(room.getId())) {
            int hintUsedCount = Math.toIntExact(hintRepository.countByRoomId(room.getId()));
            playResultRepository.save(new PlayResult(
                    room, room.getMysteryCase(), false, room.getTimeLimitSec(), hintUsedCount, now));
        }
    }

    /**
     * 남은 시간이 없는지 (now까지 누적 기준)
     */
    public boolean isTimeUp(Room room, LocalDateTime now) {
        return elapsedAt(room, now) >= room.timeLimitMs();
    }

    /**
     * 정산이 끝난 세션의 시간 응답. 유예 구간의 소진은 remainingMs 0으로만 표시한다
     */
    public RoomTimerResponse timerOf(Room room, LocalDateTime now) {
        RoomTimeCalculator.RoomTime roomTime = RoomTimeCalculator.calculate(room, now);
        boolean awaitingGrading = !RoomStatusFilter.isEnded(room.getStatus())
                && roomTime.remainingMs() == 0L
                && hasUnresolvedAccusation(room);

        return new RoomTimerResponse(
                room.getStatus(),
                room.getTimeLimitSec(),
                roomTime.elapsedActiveMs(),
                roomTime.remainingMs(),
                awaitingGrading,
                now
        );
    }

    private boolean hasUnresolvedAccusation(Room room) {
        return accusationRepository.existsByRoomIdAndGradingStatusIn(room.getId(), UNRESOLVED_GRADING_STATUSES);
    }

    private static long elapsedAt(Room room, LocalDateTime at) {
        return RoomTimeCalculator.calculate(room, at).elapsedActiveMs();
    }

    // start·resume이 항상 채우지만, 비어 있으면 진행 구간 시작 시각을 마지막 접속 확인으로 본다
    private static LocalDateTime lastSeenOf(Room room) {
        return room.getLastSeenAt() != null ? room.getLastSeenAt() : room.getActiveSince();
    }
}
