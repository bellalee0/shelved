package com.shelved.domain.accusation.dto;

import java.time.LocalDateTime;

/**
 * 429 GRADING_RETRY_TOO_SOON의 detail
 */
public record GradingRetryTooSoonDetail(
        LocalDateTime retryAvailableAt
) {
}
