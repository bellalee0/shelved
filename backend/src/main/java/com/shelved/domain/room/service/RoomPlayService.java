package com.shelved.domain.room.service;

import static com.shelved.common.exception.ErrorCode.CONNECTION_ID_REQUIRED;
import static com.shelved.common.exception.ErrorCode.CONNECTION_REPLACED;
import static com.shelved.common.exception.ErrorCode.ROOM_NOT_IN_PROGRESS;
import static com.shelved.common.exception.ErrorCode.ROOM_TIME_EXPIRED;

import com.shelved.common.entity.Room;
import com.shelved.common.enums.RoomStatus;
import com.shelved.common.exception.CustomException;
import com.shelved.common.jwt.AuthUser;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 플레이 요청(물증 조사·노트 저장·NPC 질문·힌트·지목 제출) 공통 검사 (docs/api/investigation.md "세션 상태에 따른 공통 실패").
 * 호출하는 쪽의 트랜잭션에 참여한다(MANDATORY). 반환한 Room은 그 트랜잭션이 끝날 때까지 잠겨 있다.
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class RoomPlayService {

    private final RoomAccessService roomAccessService;
    private final RoomTimeSettler roomTimeSettler;
    private final RoomSettlementService roomSettlementService;
    private final Clock clock;

    /**
     * 1. 접근(404) → 2. 헤더(400) → 3. 정산 1·2 → 4. 상태(409 ROOM_NOT_IN_PROGRESS) → 5. 시간(만료 처리 후 409 ROOM_TIME_EXPIRED)
     * → 6. 연결(409 CONNECTION_REPLACED) → 7. 접속 확인(last_seen_at = now)
     * 4·5에서 거부하면 요청 트랜잭션은 롤백되지만, 정산·만료 결과는 롤백 직후 별도 트랜잭션에서 확정된다 (RoomSettlementService)
     */
    public Room acquirePlayableRoom(Long roomId, AuthUser authUser, String connectionId) {

        Room room = roomAccessService.getAccessibleRoomForUpdate(roomId, authUser);
        if (connectionId == null || connectionId.isBlank()) {
            throw new CustomException(CONNECTION_ID_REQUIRED);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        boolean wasInProgress = room.getStatus() == RoomStatus.IN_PROGRESS;
        roomTimeSettler.settle(room, now);
        if (room.getStatus() != RoomStatus.IN_PROGRESS) {
            if (wasInProgress) {
                roomSettlementService.keepSettlementOnRollback(room.getId(), false);
            }
            throw new CustomException(ROOM_NOT_IN_PROGRESS);
        }
        if (roomTimeSettler.isTimeUp(room, now)) {
            roomTimeSettler.expire(room, now);
            roomSettlementService.keepSettlementOnRollback(room.getId(), true);
            throw new CustomException(ROOM_TIME_EXPIRED);
        }
        if (!room.isCurrentConnection(connectionId)) {
            throw new CustomException(CONNECTION_REPLACED);
        }

        roomTimeSettler.confirmPresence(room, now);

        return room;
    }
}
