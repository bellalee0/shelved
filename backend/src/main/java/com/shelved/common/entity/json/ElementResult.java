package com.shelved.common.entity.json;

import com.shelved.common.enums.RubricItemResult;

/**
 * 지목 채점의 항목별 판정 (ACCUSATIONS.element_results). 플레이어에게 반환하지 않는다.
 * evidence는 판정 근거가 된 답안 구절, reason은 판정 이유
 */
public record ElementResult(
        Long trickElementId,
        String itemId,
        RubricItemResult result,
        String evidence,
        String reason
) {
}
