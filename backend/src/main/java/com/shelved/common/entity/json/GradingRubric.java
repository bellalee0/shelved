package com.shelved.common.entity.json;

import java.util.List;

/**
 * 확정된 트릭 요소의 판정 기준 (CASE_TRICK_ELEMENTS.grading_rubric).
 * 초안의 Rubric과 같은 구조이며, 물증·인물 참조만 초안 키 대신 엔티티 ID로 저장한다.
 */
public record GradingRubric(
        List<RubricItem> items,
        EasyFeedback easyFeedback
) {

    /** 쉬움 난이도 피드백 참고 */
    public record EasyFeedback(
            String guideQuestion,
            List<Long> evidenceIds,
            List<Long> suspectIds
    ) {
    }
}
