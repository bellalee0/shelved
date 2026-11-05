package com.shelved.domain.room.service;

import static com.shelved.common.exception.ErrorCode.ROOM_NOT_FOUND;

import com.shelved.common.entity.Room;
import com.shelved.common.enums.UserRole;
import com.shelved.common.exception.CustomException;
import com.shelved.common.jwt.AuthUser;
import com.shelved.domain.room.repository.RoomParticipantRepository;
import com.shelved.domain.room.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 세션 하위 API 공통 접근 검사 (backend.md 14). 모든 세션 하위 API는 이 서비스로 세션을 조회한다.
 * - 본인이 참가한 세션이 아니면 존재 여부를 숨기고 404 ROOM_NOT_FOUND
 * - 테스트 세션(is_test)은 관리자가 아니면 404 ROOM_NOT_FOUND
 * 호출하는 쪽의 트랜잭션에 참여한다(MANDATORY). 반환한 Room의 지연 로딩·잠금은 그 트랜잭션 안에서만 유효하다.
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class RoomAccessService {

    private final RoomRepository roomRepository;
    private final RoomParticipantRepository roomParticipantRepository;

    /**
     * 조회용. 사건(mysteryCase)을 함께 읽는다
     */
    public Room getAccessibleRoom(Long roomId, AuthUser authUser) {
        Room room = roomRepository.findRoomWithCaseById(roomId);
        validateAccess(room, authUser);

        return room;
    }

    /**
     * 상태 변경용. rooms 행을 PESSIMISTIC_WRITE로 잠근 뒤 검사한다 (backend.md 15).
     * 접근할 수 없는 세션도 잠금은 잡히지만, 트랜잭션이 예외로 끝나면서 바로 풀린다
     */
    public Room getAccessibleRoomForUpdate(Long roomId, AuthUser authUser) {
        Room room = roomRepository.findRoomWithLockById(roomId);
        validateAccess(room, authUser);

        return room;
    }

    private void validateAccess(Room room, AuthUser authUser) {
        if (!roomParticipantRepository.existsByRoomIdAndUserId(room.getId(), authUser.getId())) {
            throw new CustomException(ROOM_NOT_FOUND);
        }
        if (room.isTest() && authUser.getRole() != UserRole.ADMIN) {
            throw new CustomException(ROOM_NOT_FOUND);
        }
    }
}
