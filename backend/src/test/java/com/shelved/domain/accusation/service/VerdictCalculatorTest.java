package com.shelved.domain.accusation.service;

import static com.shelved.common.enums.RubricItemResult.INCORRECT;
import static com.shelved.common.enums.RubricItemResult.MET;
import static com.shelved.common.enums.RubricItemResult.MISSING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shelved.common.enums.RubricItemResult;
import com.shelved.common.enums.Verdict;
import java.util.List;
import org.junit.jupiter.api.Test;

class VerdictCalculatorTest {

    @Test
    void calculate_범인틀림_전부MET이어도_INCORRECT() {

        // given
        List<RubricItemResult> results = List.of(MET, MET, MET);

        // when
        Verdict verdict = VerdictCalculator.calculate(false, results);

        // then
        assertThat(verdict).isEqualTo(Verdict.INCORRECT);
    }

    @Test
    void calculate_범인맞음_전부MET_CORRECT() {

        // given
        List<RubricItemResult> results = List.of(MET, MET, MET);

        // when
        Verdict verdict = VerdictCalculator.calculate(true, results);

        // then
        assertThat(verdict).isEqualTo(Verdict.CORRECT);
    }

    @Test
    void calculate_범인맞음_MET1개_PARTIAL() {

        // given
        List<RubricItemResult> results = List.of(MISSING, MET, INCORRECT);

        // when
        Verdict verdict = VerdictCalculator.calculate(true, results);

        // then
        assertThat(verdict).isEqualTo(Verdict.PARTIAL);
    }

    @Test
    void calculate_범인맞음_MET하나빼고전부_PARTIAL() {

        // given
        List<RubricItemResult> results = List.of(MET, MET, MISSING);

        // when
        Verdict verdict = VerdictCalculator.calculate(true, results);

        // then
        assertThat(verdict).isEqualTo(Verdict.PARTIAL);
    }

    @Test
    void calculate_범인맞음_MET0개_INCORRECT() {

        // given
        List<RubricItemResult> results = List.of(MISSING, INCORRECT, MISSING);

        // when
        Verdict verdict = VerdictCalculator.calculate(true, results);

        // then
        assertThat(verdict).isEqualTo(Verdict.INCORRECT);
    }

    @Test
    void calculate_항목1개사건_MET이면CORRECT_아니면INCORRECT() {

        // when
        Verdict met = VerdictCalculator.calculate(true, List.of(MET));
        Verdict missing = VerdictCalculator.calculate(true, List.of(MISSING));
        Verdict incorrect = VerdictCalculator.calculate(true, List.of(INCORRECT));

        // then 항목이 1개면 부분 정답은 없다
        assertThat(met).isEqualTo(Verdict.CORRECT);
        assertThat(missing).isEqualTo(Verdict.INCORRECT);
        assertThat(incorrect).isEqualTo(Verdict.INCORRECT);
    }

    @Test
    void calculate_항목0개_전부충족으로보지않고예외() {

        // when & then
        assertThatThrownBy(() -> VerdictCalculator.calculate(true, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
