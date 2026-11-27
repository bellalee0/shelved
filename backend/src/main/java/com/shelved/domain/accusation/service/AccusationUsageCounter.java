package com.shelved.domain.accusation.service;

import com.shelved.common.config.GameProperties;
import com.shelved.common.enums.GradingStatus;
import com.shelved.domain.accusation.repository.AccusationRepository;
import com.shelved.domain.room.dto.RoomStateResponse.RoomUsageCount;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 지목 횟수 계산 (docs/api/room.md UsageCount). 세션 상태·지목 기록·채점 확정이 같은 계산을 쓴다. 호출하는 쪽의 트랜잭션 안에서 쓴다
 *
 * <ul>
 *     <li>used = 정상 채점 완료(COMPLETED) 지목 수. 채점 실패(ERROR)는 기회를 쓰지 않는다</li>
 *     <li>reserved = 채점 중(PENDING) 지목 수. 세션당 동시 1건이라 0 또는 1</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class AccusationUsageCounter {

    private final AccusationRepository accusationRepository;
    private final GameProperties gameProperties;

    public RoomUsageCount countAccusationUsage(Long roomId) {
        return new RoomUsageCount(
                gameProperties.accusationLimit(),
                accusationRepository.countByRoomIdAndGradingStatus(roomId, GradingStatus.COMPLETED),
                accusationRepository.countByRoomIdAndGradingStatus(roomId, GradingStatus.PENDING));
    }
}
