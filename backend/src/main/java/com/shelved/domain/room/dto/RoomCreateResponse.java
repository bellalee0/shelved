package com.shelved.domain.room.dto;

import com.shelved.common.entity.Room;
import com.shelved.common.enums.RoomStatus;

public record RoomCreateResponse(
        Long roomId,
        Long caseId,
        RoomStatus status
) {

    public static RoomCreateResponse from(Room room) {
        return new RoomCreateResponse(
                room.getId(),
                room.getMysteryCase().getId(),
                room.getStatus()
        );
    }
}
