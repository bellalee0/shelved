package com.shelved.domain.room.service;

import static com.shelved.common.exception.ErrorCode.CONNECTION_ID_REQUIRED;
import static com.shelved.common.exception.ErrorCode.CONNECTION_REPLACED;
import static com.shelved.common.exception.ErrorCode.ROOM_NOT_PAUSED;
import static com.shelved.common.exception.ErrorCode.ROOM_NOT_READY;
import static com.shelved.common.exception.ErrorCode.ROOM_TIME_EXPIRED;

import com.shelved.common.config.GameProperties;
import com.shelved.common.entity.MysteryCase;
import com.shelved.common.entity.Room;
import com.shelved.common.enums.AiRequestStatus;
import com.shelved.common.enums.AiRequestType;
import com.shelved.common.enums.GradingStatus;
import com.shelved.common.enums.RoomStatus;
import com.shelved.common.exception.CustomException;
import com.shelved.common.jwt.AuthUser;
import com.shelved.common.utils.SafeExceptionLog;
import com.shelved.domain.accusation.repository.AccusationRepository;
import com.shelved.domain.airequest.service.AiRequestQueryService;
import com.shelved.domain.hint.repository.HintRepository;
import com.shelved.domain.room.dto.RoomConnectionResponse;
import com.shelved.domain.room.dto.RoomStateResponse;
import com.shelved.domain.room.dto.RoomStateResponse.RoomUsageCount;
import com.shelved.domain.room.dto.RoomTimerResponse;
import com.shelved.domain.room.repository.AiRequestRepository;
import com.shelved.domain.room.repository.RoomRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 세션 시간 라이프사이클: 상태 조회·시작·하트비트·일시정지·이어하기·이탈 감지.
 * 모든 상태 변경은 rooms 행을 PESSIMISTIC_WRITE로 잠근 뒤 RoomTimeSettler로 먼저 정산한다 (backend.md 15)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoomLifecycleService {

    private final RoomRepository roomRepository;
    private final AccusationRepository accusationRepository;
    private final HintRepository hintRepository;
    private final AiRequestRepository aiRequestRepository;
    private final AiRequestQueryService aiRequestQueryService;
    private final RoomAccessService roomAccessService;
    private final RoomTimeSettler roomTimeSettler;
    private final RoomSettlementService roomSettlementService;
    private final GameProperties gameProperties;
    private final Clock clock;

    /**
     * 세션 상태. 모든 상태에서 조회할 수 있고, 정산 1·2만 적용한다(접속 확인 아님)
     */
    @Transactional
    public RoomStateResponse getRoomState(AuthUser authUser, Long roomId) {

        Room room = roomAccessService.getAccessibleRoomForUpdate(roomId, authUser);
        LocalDateTime now = LocalDateTime.now(clock);
        roomTimeSettler.settle(room, now);

        MysteryCase mysteryCase = room.getMysteryCase();
        RoomUsageCount hintCount = new RoomUsageCount(
                gameProperties.hintLimitOf(mysteryCase.getDifficulty()),
                hintRepository.countByRoomId(roomId),
                aiRequestRepository.countByRoomIdAndRequestTypeAndStatus(roomId, AiRequestType.HINT, AiRequestStatus.PROCESSING));
        RoomUsageCount accusationCount = new RoomUsageCount(
                gameProperties.accusationLimit(),
                accusationRepository.countByRoomIdAndGradingStatus(roomId, GradingStatus.COMPLETED),
                accusationRepository.countByRoomIdAndGradingStatus(roomId, GradingStatus.PENDING));

        return new RoomStateResponse(
                room.getId(),
                mysteryCase.getId(),
                mysteryCase.getDisplayTitle(),
                mysteryCase.getDifficulty(),
                room.isTest(),
                room.getStartedAt(),
                roomTimeSettler.timerOf(room, now),
                heartbeatIntervalMs(),
                hintCount,
                accusationCount,
                null,
                aiRequestQueryService.getActiveAiRequestList(roomId),
                List.of()
        );
    }

    /**
     * 수사 시작. READY만 가능
     */
    @Transactional
    public RoomConnectionResponse startRoom(AuthUser authUser, Long roomId) {

        Room room = roomAccessService.getAccessibleRoomForUpdate(roomId, authUser);
        if (room.getStatus() != RoomStatus.READY) {
            throw new CustomException(ROOM_NOT_READY);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        String connectionId = newConnectionId();
        room.start(now, connectionId);

        return new RoomConnectionResponse(connectionId, heartbeatIntervalMs(), roomTimeSettler.timerOf(room, now));
    }

    /**
     * 하트비트. IN_PROGRESS가 아니면 연결 검사 없이 상태만 반환하며 자동 재개하지 않는다
     */
    @Transactional
    public RoomTimerResponse recordHeartbeat(AuthUser authUser, Long roomId, String connectionId) {

        Room room = roomAccessService.getAccessibleRoomForUpdate(roomId, authUser);
        if (connectionId == null || connectionId.isBlank()) {
            throw new CustomException(CONNECTION_ID_REQUIRED);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        roomTimeSettler.settle(room, now);
        if (room.getStatus() != RoomStatus.IN_PROGRESS) {
            return roomTimeSettler.timerOf(room, now);
        }
        // 여기까지 IN_PROGRESS이면 정산이 상태를 바꾸지 않았으므로 거부해도 잃는 변경이 없다
        if (!room.isCurrentConnection(connectionId)) {
            throw new CustomException(CONNECTION_REPLACED);
        }

        roomTimeSettler.confirmPresence(room, now);

        return roomTimeSettler.timerOf(room, now);
    }

    /**
     * 일시정지. 연결 헤더 불필요, IN_PROGRESS가 아니면 바꾸지 않고 현재 상태를 반환한다(중복 호출 안전)
     */
    @Transactional
    public RoomTimerResponse pauseRoom(AuthUser authUser, Long roomId) {

        Room room = roomAccessService.getAccessibleRoomForUpdate(roomId, authUser);
        LocalDateTime now = LocalDateTime.now(clock);
        roomTimeSettler.pause(room, now);

        return roomTimeSettler.timerOf(room, now);
    }

    /**
     * 수사 이어하기. PAUSED이고 남은 시간이 있을 때만 가능하며, 새 연결로 이전 연결을 무효화한다
     */
    @Transactional
    public RoomConnectionResponse resumeRoom(AuthUser authUser, Long roomId) {

        Room room = roomAccessService.getAccessibleRoomForUpdate(roomId, authUser);
        LocalDateTime now = LocalDateTime.now(clock);
        boolean wasInProgress = room.getStatus() == RoomStatus.IN_PROGRESS;
        roomTimeSettler.settle(room, now);
        if (room.getStatus() != RoomStatus.PAUSED) {
            if (wasInProgress) {
                roomSettlementService.keepSettlementOnRollback(room.getId(), false);
            }
            throw new CustomException(ROOM_NOT_PAUSED);
        }
        // 시간 소진 후 채점 대기로 보류된 세션
        if (roomTimeSettler.isTimeUp(room, now)) {
            throw new CustomException(ROOM_TIME_EXPIRED);
        }

        String connectionId = newConnectionId();
        room.resume(now, connectionId);

        return new RoomConnectionResponse(connectionId, heartbeatIntervalMs(), roomTimeSettler.timerOf(room, now));
    }

    /**
     * 이탈 감지 주기 점검. 후보 ID를 먼저 읽고 세션마다 별도의 짧은 트랜잭션에서 잠금 → 재확인 → 정산한다.
     * 한 세션이 실패해도 나머지를 계속 처리한다
     */
    public void pauseDisconnectedRooms() {

        LocalDateTime seenBefore = LocalDateTime.now(clock).minus(gameProperties.heartbeatTimeout());
        List<Long> roomIdList = roomRepository.findIdsByStatusAndLastSeenAtLessThanEqual(RoomStatus.IN_PROGRESS, seenBefore);

        for (Long roomId : roomIdList) {
            try {
                roomSettlementService.settleRoom(roomId);
            } catch (RuntimeException e) {
                log.error("이탈 감지 정산 실패 roomId={} exception={}{}",
                        roomId, SafeExceptionLog.causeChain(e), SafeExceptionLog.rootCauseFrames(e));
            }
        }
    }

    private long heartbeatIntervalMs() {
        return gameProperties.heartbeatInterval().toMillis();
    }

    // 추측할 수 없는 임의 값. 로그·예외 메시지에 남기지 않는다
    private static String newConnectionId() {
        return UUID.randomUUID().toString();
    }
}
