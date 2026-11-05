package com.shelved.domain.room.repository;

import com.shelved.common.entity.AiRequest;
import com.shelved.common.enums.AiRequestStatus;
import com.shelved.common.enums.AiRequestType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiRequestRepository extends JpaRepository<AiRequest, Long> {

    long countByRoomIdAndRequestTypeAndStatus(Long roomId, AiRequestType requestType, AiRequestStatus status);
}
