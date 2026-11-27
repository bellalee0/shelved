package com.shelved.domain.accusation.dto;

import java.time.LocalDateTime;

/**
 * 502 GRADING_FAILED의 detail
 *
 * @param retryAvailableAt 같은 답안을 다시 채점할 수 있는 시각
 */
public record AccusationFailureDetail(
        String requestId,
        Long accusationId,
        LocalDateTime retryAvailableAt
) {
}
