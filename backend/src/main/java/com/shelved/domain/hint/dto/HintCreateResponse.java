package com.shelved.domain.hint.dto;

import com.shelved.domain.room.dto.RoomStateResponse.RoomUsageCount;

/**
 * 새 힌트 POST의 성공 응답. 최초 성공 당시의 스냅샷으로 저장되어 재전송·상태 조회에 그대로 쓰인다
 *
 * @param hintCount 이 힌트의 사용을 반영한 뒤의 횟수 (당시 값, 현재 값으로 재계산하지 않음)
 */
public record HintCreateResponse(
        HintResponse hint,
        RoomUsageCount hintCount
) {
}
