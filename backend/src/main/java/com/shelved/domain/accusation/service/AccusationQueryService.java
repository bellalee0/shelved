package com.shelved.domain.accusation.service;

import com.shelved.common.config.GameProperties;
import com.shelved.common.entity.Accusation;
import com.shelved.common.entity.AiRequest;
import com.shelved.common.enums.AiRequestType;
import com.shelved.common.enums.GradingStatus;
import com.shelved.domain.accusation.dto.AccusationResponse;
import com.shelved.domain.accusation.repository.AccusationRepository;
import com.shelved.domain.room.repository.AiRequestRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 지목 기록 조회 (GET accusations, GET state의 pendingAccusation). 세션 접근 검사는 호출하는 쪽에서 하고, 그 트랜잭션에 참여한다.
 * 지목(인물 포함)과 지목 요청 행을 각각 한 번에 읽어 묶는다 (N+1 없음)
 */
@Service
@RequiredArgsConstructor
public class AccusationQueryService {

    private final AccusationRepository accusationRepository;
    private final AiRequestRepository aiRequestRepository;
    private final GameProperties gameProperties;

    /**
     * 세션의 지목 기록 (오래된 순). attemptNo는 COMPLETED 지목의 접수 순서(1부터)
     */
    @Transactional(readOnly = true)
    public List<AccusationResponse> getAccusationResponseList(Long roomId) {

        Map<String, AiRequest> aiRequestByRequestId = accusationRequestMapOf(roomId);

        List<AccusationResponse> responseList = new ArrayList<>();
        int completedCount = 0;
        for (Accusation accusation : accusationRepository.findAllWithSuspectByRoomId(roomId)) {
            Integer attemptNo = null;
            if (accusation.getGradingStatus() == GradingStatus.COMPLETED) {
                attemptNo = ++completedCount;
            }
            responseList.add(AccusationResponse.of(
                    accusation, aiRequestByRequestId.get(accusation.getRequestId()), attemptNo, gameProperties.gradingRetryMinInterval()));
        }
        return responseList;
    }

    /**
     * 채점 중(PENDING)이거나 채점 실패(ERROR)한, 대체되지 않은 가장 최근 지목. 없으면 null
     */
    @Transactional(readOnly = true)
    public AccusationResponse getPendingAccusation(Long roomId) {

        List<Accusation> unresolvedList = accusationRepository.findNotSupersededByRoomIdAndGradingStatusIn(
                roomId, AccusationRepository.UNRESOLVED_GRADING_STATUSES);
        if (unresolvedList.isEmpty()) {
            return null;
        }

        Accusation latest = unresolvedList.getFirst();
        AiRequest aiRequest = aiRequestRepository.findByRoomIdAndRequestId(roomId, latest.getRequestId()).orElse(null);

        return AccusationResponse.of(latest, aiRequest, null, gameProperties.gradingRetryMinInterval());
    }

    private Map<String, AiRequest> accusationRequestMapOf(Long roomId) {
        return aiRequestRepository.findByRoomIdAndRequestType(roomId, AiRequestType.ACCUSATION).stream()
                .collect(Collectors.toMap(AiRequest::getRequestId, Function.identity()));
    }
}
