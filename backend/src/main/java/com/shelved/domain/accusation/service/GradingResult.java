package com.shelved.domain.accusation.service;

import com.shelved.common.entity.json.ElementResult;
import com.shelved.common.enums.Verdict;
import java.util.List;

/**
 * 채점 결과. Accusation.completeGrading(culpritCorrect, verdict, elementResults, feedback)에 그대로 넘긴다.
 * elementResultList는 정답 보호 대상(플레이어에게 반환하지 않음)이므로 로그에 남기지 않는다
 */
public record GradingResult(
        boolean culpritCorrect,
        Verdict verdict,
        List<ElementResult> elementResultList,
        String feedback
) {

    public GradingResult {
        elementResultList = List.copyOf(elementResultList);
    }

    @Override
    public String toString() {
        return "GradingResult[culpritCorrect=" + culpritCorrect + ", verdict=" + verdict
                + ", itemCount=" + elementResultList.size() + "]";
    }
}
