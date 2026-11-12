package com.shelved.domain.airequest.service;

import static com.shelved.common.exception.ErrorCode.ROOM_ENDED_DURING_REQUEST;

import com.shelved.common.config.GameProperties;
import com.shelved.common.entity.AiRequest;
import com.shelved.common.entity.Room;
import com.shelved.common.enums.AiRequestStatus;
import com.shelved.common.enums.AiRequestType;
import com.shelved.common.jwt.AuthUser;
import com.shelved.domain.airequest.dto.AiRequestStatusResponse;
import com.shelved.domain.room.repository.AiRequestRepository;
import com.shelved.domain.room.service.RoomAccessService;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 요청 상태 조회. rooms 행을 잠그지 않으므로 AI 호출·확정과 관계없이 바로 응답한다
 */
@Service
@RequiredArgsConstructor
public class AiRequestQueryService {

    // 같은 대상의 요청 중 가장 나중에 갱신된 것
    private static final Comparator<AiRequest> LATEST_FIRST = Comparator
            .comparing(AiRequest::getUpdatedAt)
            .thenComparing(AiRequest::getId);

    private final AiRequestRepository aiRequestRepository;
    private final RoomAccessService roomAccessService;
    private final GameProperties gameProperties;

    /**
     * AI 요청 상태. 참가자만 조회할 수 있고(아니면 404 ROOM_NOT_FOUND), 일시정지·종료 후에도 조회할 수 있다
     */
    @Transactional(readOnly = true)
    public AiRequestStatusResponse getAiRequest(AuthUser authUser, Long roomId, String requestId) {

        Room room = roomAccessService.getAccessibleRoom(roomId, authUser);
        AiRequest aiRequest = aiRequestRepository.findAiRequest(room.getId(), requestId);

        return statusOf(aiRequest);
    }

    /**
     * 세션 상태의 aiRequests: PROCESSING 요청 전부와, 재시도 대기 중인 FAILED 요청.
     * 재시도 대기 = 같은 대상(유형 + targetId)의 가장 최근 요청이고, 대체되지 않았고(지목), 세션 종료로 실패한 것이 아닌 FAILED.
     * 같은 대상에 이후 다른 요청을 보냈다면 이전 실패는 포함하지 않는다. 접수 순서로 정렬한다.
     * 호출하는 쪽의 트랜잭션에 참여한다 (세션 접근 검사는 호출하는 쪽에서 한다)
     */
    @Transactional(readOnly = true)
    public List<AiRequestStatusResponse> getActiveAiRequestList(Long roomId) {

        List<AiRequest> aiRequestList = aiRequestRepository.findByRoomIdOrderByIdAsc(roomId);

        Map<TargetKey, AiRequest> latestByTarget = new HashMap<>();
        for (AiRequest aiRequest : aiRequestList) {
            latestByTarget.merge(TargetKey.of(aiRequest), aiRequest,
                    (current, candidate) -> LATEST_FIRST.compare(candidate, current) > 0 ? candidate : current);
        }

        return aiRequestList.stream()
                .filter(aiRequest -> aiRequest.getStatus() == AiRequestStatus.PROCESSING
                        || (latestByTarget.get(TargetKey.of(aiRequest)) == aiRequest && isAwaitingRetry(aiRequest)))
                .map(this::statusOf)
                .toList();
    }

    private AiRequestStatusResponse statusOf(AiRequest aiRequest) {
        return AiRequestStatusResponse.from(aiRequest, gameProperties.gradingRetryMinInterval());
    }

    private static boolean isAwaitingRetry(AiRequest aiRequest) {
        return aiRequest.getStatus() == AiRequestStatus.FAILED
                && aiRequest.getSupersededAt() == null
                && !ROOM_ENDED_DURING_REQUEST.name().equals(aiRequest.getErrorCode());
    }

    private record TargetKey(AiRequestType requestType, Long targetId) {

        static TargetKey of(AiRequest aiRequest) {
            return new TargetKey(aiRequest.getRequestType(), aiRequest.getTargetId());
        }
    }
}
