package com.shelved.domain.accusation.dto;

import com.shelved.common.enums.RoomStatus;
import com.shelved.domain.room.dto.RoomStateResponse.RoomUsageCount;

/**
 * 지목 채점 성공 (POST accusations 200). result_json 스냅샷으로도 저장된다 (재전송·상태 조회는 당시 값)
 *
 * @param accusation      gradingStatus=COMPLETED
 * @param accusationCount 반영 후 횟수
 * @param roomStatus      반영 후 세션 상태. CLEARED/FAILED/EXPIRED면 결과 화면으로 이동
 */
public record AccusationCreateResponse(
        AccusationResponse accusation,
        RoomUsageCount accusationCount,
        RoomStatus roomStatus
) {
}
