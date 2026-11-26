package com.shelved.domain.accusation.service;

import com.shelved.common.enums.RubricItemResult;
import com.shelved.common.enums.Verdict;
import java.util.List;

/**
 * 최종 판정 계산 (AI에게 묻지 않고 서버가 계산한다). 항목은 모든 요소의 모든 필수 항목이다
 *
 * <pre>
 * 범인 틀림                       → INCORRECT
 * 범인 맞음 + 전부 MET            → CORRECT
 * 범인 맞음 + 1개 이상 MET(전부는 아님) → PARTIAL
 * 범인 맞음 + MET 0개             → INCORRECT
 * </pre>
 */
public final class VerdictCalculator {

    private VerdictCalculator() {
    }

    /**
     * @throws IllegalArgumentException 항목이 없으면 (항목 0개를 "전부 충족"으로 보지 않는다)
     */
    public static Verdict calculate(boolean culpritCorrect, List<RubricItemResult> resultList) {

        if (resultList == null || resultList.isEmpty()) {
            throw new IllegalArgumentException("판정 항목이 없으면 최종 판정을 계산할 수 없습니다.");
        }
        if (!culpritCorrect) {
            return Verdict.INCORRECT;
        }

        long metCount = resultList.stream()
                .filter(result -> result == RubricItemResult.MET)
                .count();
        if (metCount == resultList.size()) {
            return Verdict.CORRECT;
        }
        return metCount > 0 ? Verdict.PARTIAL : Verdict.INCORRECT;
    }
}
