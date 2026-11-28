package com.shelved.domain.accusation.service;

import com.shelved.common.entity.json.ElementResult;
import com.shelved.common.enums.RubricItemResult;
import com.shelved.common.enums.Verdict;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 최종 판정 계산 (AI에게 묻지 않고 서버가 계산한다). 항목은 모든 요소의 모든 필수 항목이다.
 * 요소는 MET 수가 ceil(항목 수 × 기준 비율) 이상이면 통과한다
 *
 * <pre>
 * 범인 틀림                                          → INCORRECT
 * 범인 맞음 + INCORRECT 항목 0개 + 모든 요소 통과       → CORRECT
 * 범인 맞음 + MET 1개 이상 (위 조건 미충족)             → PARTIAL
 * 범인 맞음 + MET 0개                                 → INCORRECT
 * </pre>
 */
public final class VerdictCalculator {

    private VerdictCalculator() {
    }

    /**
     * @param resultList       모든 요소의 항목별 판정. 요소는 trickElementId로 묶는다.
     *                         항목이 0개인 요소는 여기에 나타나지 않으므로 GradingContext가 미리 거부한다
     * @param elementPassRatio 요소 통과 기준 비율 (0 초과 1 이하)
     * @throws IllegalArgumentException 항목이 없으면 (항목 0개를 "전부 충족"으로 보지 않는다)
     */
    public static Verdict calculate(boolean culpritCorrect, List<ElementResult> resultList, BigDecimal elementPassRatio) {

        if (resultList == null || resultList.isEmpty()) {
            throw new IllegalArgumentException("판정 항목이 없으면 최종 판정을 계산할 수 없습니다.");
        }
        if (!culpritCorrect || resultList.stream().noneMatch(item -> item.result() == RubricItemResult.MET)) {
            return Verdict.INCORRECT;
        }
        if (resultList.stream().anyMatch(item -> item.result() == RubricItemResult.INCORRECT)) {
            return Verdict.PARTIAL;
        }

        Map<Long, List<ElementResult>> resultListByElementId = resultList.stream()
                .collect(Collectors.groupingBy(ElementResult::trickElementId));
        boolean allElementPassed = resultListByElementId.values().stream()
                .allMatch(elementResultList -> isPassed(elementResultList, elementPassRatio));
        return allElementPassed ? Verdict.CORRECT : Verdict.PARTIAL;
    }

    // 부동소수점 오차로 올림 결과가 달라지지 않도록 BigDecimal로 계산한다
    private static boolean isPassed(List<ElementResult> elementResultList, BigDecimal elementPassRatio) {

        long metCount = elementResultList.stream()
                .filter(item -> item.result() == RubricItemResult.MET)
                .count();
        long requiredCount = elementPassRatio.multiply(BigDecimal.valueOf(elementResultList.size()))
                .setScale(0, RoundingMode.CEILING)
                .longValueExact();
        return metCount >= requiredCount;
    }
}
