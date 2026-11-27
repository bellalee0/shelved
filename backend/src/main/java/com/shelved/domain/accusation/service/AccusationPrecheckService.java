package com.shelved.domain.accusation.service;

import static com.shelved.common.exception.ErrorCode.ROOM_TIME_EXPIRED;

import com.shelved.common.entity.Room;
import com.shelved.common.enums.RoomStatus;
import com.shelved.common.exception.CustomException;
import com.shelved.common.jwt.AuthUser;
import com.shelved.domain.room.repository.AiRequestRepository;
import com.shelved.domain.room.service.RoomAccessService;
import com.shelved.domain.room.service.RoomSettlementService;
import com.shelved.domain.room.service.RoomTimeSettler;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 지목 접수 전 짧은 검사. 마감 후 새 답안은 세션 상태와 관계없이 409 ROOM_TIME_EXPIRED로 거부한다 (docs/api/hint-accusation.md).
 * 플레이 공통 검사(RoomPlayService)는 채점 대기로 보류된 PAUSED 세션을 ROOM_NOT_IN_PROGRESS로 거부하므로 그보다 먼저 확인한다.
 * 진행 중 세션의 만료 확정·연결 검사는 접수 트랜잭션의 공통 검사가 그대로 한다(진행 중에 시간이 다 됐으면 거기서 ROOM_TIME_EXPIRED)
 */
@Service
@RequiredArgsConstructor
public class AccusationPrecheckService {

    private final RoomAccessService roomAccessService;
    private final RoomTimeSettler roomTimeSettler;
    private final RoomSettlementService roomSettlementService;
    private final AiRequestRepository aiRequestRepository;
    private final Clock clock;

    /**
     * 새 답안이고(같은 requestId의 요청이 없음) 남은 시간이 없는 세션(채점 대기 PAUSED·EXPIRED)이면 409 ROOM_TIME_EXPIRED.
     * 같은 requestId가 있으면(재전송·재채점) 아무것도 하지 않고 접수 단계에 맡긴다
     */
    @Transactional
    public void validateNewAnswerTime(AuthUser authUser, Long roomId, String requestId) {

        Room room = roomAccessService.getAccessibleRoomForUpdate(roomId, authUser);
        if (aiRequestRepository.findByRoomIdAndRequestId(room.getId(), requestId).isPresent()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        boolean wasInProgress = room.getStatus() == RoomStatus.IN_PROGRESS;
        roomTimeSettler.settle(room, now);
        boolean awaitingGrading = room.getStatus() == RoomStatus.PAUSED && roomTimeSettler.isTimeUp(room, now);
        if (room.getStatus() == RoomStatus.EXPIRED || awaitingGrading) {
            if (wasInProgress) {
                roomSettlementService.keepSettlementOnRollback(room.getId(), false);
            }
            throw new CustomException(ROOM_TIME_EXPIRED);
        }
    }
}
