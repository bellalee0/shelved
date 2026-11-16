package com.shelved.domain.airequest.service;

import static com.shelved.common.exception.ErrorCode.AI_REQUEST_ID_REUSED;

import com.shelved.common.entity.AiRequest;
import com.shelved.common.entity.Room;
import com.shelved.common.enums.AiRequestStatus;
import com.shelved.common.enums.AiRequestType;
import com.shelved.common.exception.CustomException;
import com.shelved.common.jwt.AuthUser;
import com.shelved.domain.room.repository.AiRequestRepository;
import com.shelved.domain.room.service.RoomAccessService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 접수 전 짧은 읽기 전용 확인. 질문 분류(비용이 드는 AI 호출)를 하기 전에, 같은 requestId가 이미 접수된 요청인지 본다.
 * rooms 행을 잠그지 않는다. 최종 판단은 접수 트랜잭션(AiRequestTransactionService.accept)이 다시 한다
 */
@Service
@RequiredArgsConstructor
public class AiRequestPrecheckService {

    private final AiRequestRepository aiRequestRepository;
    private final RoomAccessService roomAccessService;

    /**
     * 같은 requestId·같은 입력의 요청이 이미 성공했거나 처리 중이면 true (분류·사전 검사 없이 접수 단계로 보내 저장된 결과·202를 받는다).
     * 새 요청이거나 실패한 요청의 재시도면 false. 같은 requestId에 다른 입력(유형·대상 포함)이면 409 AI_REQUEST_ID_REUSED,
     * 참가자가 아니면 404 ROOM_NOT_FOUND
     */
    @Transactional(readOnly = true)
    public boolean isAlreadyAccepted(AuthUser authUser, AiRequestCommand command, AiRequestType requestType) {

        Room room = roomAccessService.getAccessibleRoom(command.roomId(), authUser);

        Optional<AiRequest> existing = aiRequestRepository.findByRoomIdAndRequestId(room.getId(), command.requestId());
        if (existing.isEmpty()) {
            return false;
        }
        AiRequest aiRequest = existing.get();
        if (!aiRequest.isSameRequest(requestType, command.targetId(), command.input())) {
            throw new CustomException(AI_REQUEST_ID_REUSED);
        }

        return aiRequest.getStatus() == AiRequestStatus.SUCCEEDED || aiRequest.getStatus() == AiRequestStatus.PROCESSING;
    }
}
