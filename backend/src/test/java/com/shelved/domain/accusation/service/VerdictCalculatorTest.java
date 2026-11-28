package com.shelved.domain.accusation.service;

import static com.shelved.common.enums.RubricItemResult.INCORRECT;
import static com.shelved.common.enums.RubricItemResult.MET;
import static com.shelved.common.enums.RubricItemResult.MISSING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shelved.common.entity.json.ElementResult;
import com.shelved.common.enums.RubricItemResult;
import com.shelved.common.enums.Verdict;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 가상 사건 「시계탑 저택의 마지막 종소리」의 요소별 판정 조합
 */
class VerdictCalculatorTest {

    private static final BigDecimal HALF = new BigDecimal("0.5");

    @Test
    void calculate_범인틀림_전부MET이어도_INCORRECT() {

        // given
        List<ElementResult> resultList = results(List.of(MET, MET), List.of(MET, MET));

        // when
        Verdict verdict = VerdictCalculator.calculate(false, resultList, HALF);

        // then
        assertThat(verdict).isEqualTo(Verdict.INCORRECT);
    }

    @Test
    void calculate_범인맞음_전부MET_CORRECT() {

        // given
        List<ElementResult> resultList = results(List.of(MET, MET), List.of(MET, MET));

        // when
        Verdict verdict = VerdictCalculator.calculate(true, resultList, HALF);

        // then
        assertThat(verdict).isEqualTo(Verdict.CORRECT);
    }

    @Test
    void calculate_요소마다절반이상MET_INCORRECT없음_CORRECT() {

        // given
        List<ElementResult> resultList = results(List.of(MET, MISSING), List.of(MET, MET, MISSING, MISSING));

        // when
        Verdict verdict = VerdictCalculator.calculate(true, resultList, HALF);

        // then
        assertThat(verdict).isEqualTo(Verdict.CORRECT);
    }

    @Test
    void calculate_한요소MET0개_다른요소전부MET이어도_PARTIAL() {

        // given
        List<ElementResult> resultList = results(List.of(MET, MET, MET, MET), List.of(MISSING, MISSING));

        // when
        Verdict verdict = VerdictCalculator.calculate(true, resultList, HALF);

        // then
        assertThat(verdict).isEqualTo(Verdict.PARTIAL);
    }

    @Test
    void calculate_절반이상MET이어도_INCORRECT1개면_PARTIAL() {

        // given
        List<ElementResult> resultList = results(List.of(MET, MET), List.of(MET, MET, INCORRECT));

        // when
        Verdict verdict = VerdictCalculator.calculate(true, resultList, HALF);

        // then
        assertThat(verdict).isEqualTo(Verdict.PARTIAL);
    }

    @Test
    void calculate_홀수항목요소_올림한수만큼필요() {

        // when 항목 3개 × 0.5 = 1.5 → 2개 필요
        Verdict oneMet = VerdictCalculator.calculate(true, results(List.of(MET, MISSING, MISSING)), HALF);
        Verdict twoMet = VerdictCalculator.calculate(true, results(List.of(MET, MET, MISSING)), HALF);

        // then
        assertThat(oneMet).isEqualTo(Verdict.PARTIAL);
        assertThat(twoMet).isEqualTo(Verdict.CORRECT);
    }

    @Test
    void calculate_곱이정수인비율_부동소수점오차없이계산() {

        // given 항목 10개 × 0.7 = 정확히 7개 (double이면 7.000…01 → 8개)
        List<RubricItemResult> element = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            element.add(i < 7 ? MET : MISSING);
        }

        // when
        Verdict verdict = VerdictCalculator.calculate(true, results(element), new BigDecimal("0.7"));

        // then
        assertThat(verdict).isEqualTo(Verdict.CORRECT);
    }

    @Test
    void calculate_범인맞음_MET0개_INCORRECT() {

        // given
        List<ElementResult> resultList = results(List.of(MISSING, INCORRECT), List.of(MISSING));

        // when
        Verdict verdict = VerdictCalculator.calculate(true, resultList, HALF);

        // then
        assertThat(verdict).isEqualTo(Verdict.INCORRECT);
    }

    @Test
    void calculate_기준비율1_전부MET이어야CORRECT() {

        // when
        Verdict allMet = VerdictCalculator.calculate(true, results(List.of(MET, MET), List.of(MET)), BigDecimal.ONE);
        Verdict oneMissing = VerdictCalculator.calculate(true, results(List.of(MET, MISSING), List.of(MET)), BigDecimal.ONE);

        // then
        assertThat(allMet).isEqualTo(Verdict.CORRECT);
        assertThat(oneMissing).isEqualTo(Verdict.PARTIAL);
    }

    @Test
    void calculate_항목0개_전부충족으로보지않고예외() {

        // when & then
        assertThatThrownBy(() -> VerdictCalculator.calculate(true, List.of(), HALF))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // 인자 하나가 요소 하나의 항목 판정 목록이다
    @SafeVarargs
    private static List<ElementResult> results(List<RubricItemResult>... elements) {
        List<ElementResult> resultList = new ArrayList<>();
        for (int e = 0; e < elements.length; e++) {
            for (int i = 0; i < elements[e].size(); i++) {
                resultList.add(new ElementResult(101L + e, "trk-" + (e + 1) + "-i" + (i + 1), elements[e].get(i), "", "판단 이유"));
            }
        }
        return resultList;
    }
}
