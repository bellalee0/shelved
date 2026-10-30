package com.shelved.common.entity.json;

import java.util.List;

/**
 * 트릭 요소의 필수 판정 항목. itemId는 요소 안의 고정 ID이며 채점 결과(element_results)가 참조한다.
 */
public record RubricItem(
        String itemId,
        String label,
        String criterion,
        List<String> acceptExamples,
        List<String> rejectExamples
) {
}
