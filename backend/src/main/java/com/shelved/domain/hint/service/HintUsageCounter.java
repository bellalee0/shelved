package com.shelved.domain.hint.service;

import com.shelved.common.config.GameProperties;
import com.shelved.common.enums.AiRequestStatus;
import com.shelved.common.enums.AiRequestType;
import com.shelved.common.enums.Difficulty;
import com.shelved.domain.hint.repository.HintRepository;
import com.shelved.domain.room.dto.RoomStateResponse.RoomUsageCount;
import com.shelved.domain.room.repository.AiRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 새 힌트 횟수 계산 (docs/api/room.md UsageCount). 세션 상태·힌트 기록·힌트 접수가 같은 계산을 쓴다.
 * 호출하는 쪽의 트랜잭션 안에서 쓴다 (접수·확정에서는 rooms 행 잠금 안이라 값이 바뀌지 않는다)
 *
 * <ul>
 *     <li>used = 사용 확정 힌트 수 (hints 행 수). 보충 질문은 세지 않는다</li>
 *     <li>reserved = 처리 중(PROCESSING)인 HINT 요청 수 = 임시 확보분. 세션당 동시 1건이라 0 또는 1</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class HintUsageCounter {

    private final HintRepository hintRepository;
    private final AiRequestRepository aiRequestRepository;
    private final GameProperties gameProperties;

    public RoomUsageCount countHintUsage(Long roomId, Difficulty difficulty) {
        return new RoomUsageCount(
                gameProperties.hintLimitOf(difficulty),
                hintRepository.countByRoomId(roomId),
                aiRequestRepository.countByRoomIdAndRequestTypeAndStatus(roomId, AiRequestType.HINT, AiRequestStatus.PROCESSING));
    }

    /**
     * 새 힌트를 하나 더 확보할 수 없는지 (사용 확정 + 처리 중 확보 >= 한도)
     */
    public static boolean isExhausted(RoomUsageCount hintCount) {
        return hintCount.used() + hintCount.reserved() >= hintCount.limit();
    }
}
