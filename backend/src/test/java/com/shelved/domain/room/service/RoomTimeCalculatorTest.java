package com.shelved.domain.room.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.shelved.common.enums.RoomStatus;
import com.shelved.domain.room.service.RoomTimeCalculator.RoomTime;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class RoomTimeCalculatorTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 5, 14, 0, 0);
    private static final int TIME_LIMIT_SEC = 1200;

    @Test
    void calculate_READY_경과0남은시간전체() {

        // given & when
        RoomTime time = RoomTimeCalculator.calculate(RoomStatus.READY, TIME_LIMIT_SEC, 0L, null, NOW);

        // then
        assertThat(time.elapsedActiveMs()).isZero();
        assertThat(time.remainingMs()).isEqualTo(1_200_000L);
    }

    @Test
    void calculate_IN_PROGRESS_activeSince이후구간포함() {

        // given: 이전 누적 60초 + 현재 구간 90.5초
        LocalDateTime activeSince = NOW.minusSeconds(90).minusNanos(500_000_000);

        // when
        RoomTime time = RoomTimeCalculator.calculate(RoomStatus.IN_PROGRESS, TIME_LIMIT_SEC, 60_000L, activeSince, NOW);

        // then
        assertThat(time.elapsedActiveMs()).isEqualTo(150_500L);
        assertThat(time.remainingMs()).isEqualTo(1_049_500L);
    }

    @Test
    void calculate_IN_PROGRESS_activeSince없음_누적값만() {

        // given & when
        RoomTime time = RoomTimeCalculator.calculate(RoomStatus.IN_PROGRESS, TIME_LIMIT_SEC, 60_000L, null, NOW);

        // then
        assertThat(time.elapsedActiveMs()).isEqualTo(60_000L);
    }

    @Test
    void calculate_IN_PROGRESS_activeSince가미래_음수구간더하지않음() {

        // given & when
        RoomTime time = RoomTimeCalculator.calculate(RoomStatus.IN_PROGRESS, TIME_LIMIT_SEC, 60_000L, NOW.plusSeconds(5), NOW);

        // then
        assertThat(time.elapsedActiveMs()).isEqualTo(60_000L);
    }

    @Test
    void calculate_PAUSED_activeSince가있어도현재구간제외() {

        // given & when
        RoomTime time = RoomTimeCalculator.calculate(RoomStatus.PAUSED, TIME_LIMIT_SEC, 300_000L, NOW.minusMinutes(10), NOW);

        // then
        assertThat(time.elapsedActiveMs()).isEqualTo(300_000L);
        assertThat(time.remainingMs()).isEqualTo(900_000L);
    }

    @ParameterizedTest
    @EnumSource(value = RoomStatus.class, names = {"CLEARED", "FAILED", "EXPIRED"})
    void calculate_종료상태_남은시간0(RoomStatus status) {

        // given & when
        RoomTime time = RoomTimeCalculator.calculate(status, TIME_LIMIT_SEC, 300_000L, null, NOW);

        // then
        assertThat(time.elapsedActiveMs()).isEqualTo(300_000L);
        assertThat(time.remainingMs()).isZero();
    }

    @Test
    void calculate_제한시간초과_남은시간0미만으로내려가지않음() {

        // given: 누적 19분 + 현재 구간 2분 > 20분
        LocalDateTime activeSince = NOW.minusMinutes(2);

        // when
        RoomTime time = RoomTimeCalculator.calculate(RoomStatus.IN_PROGRESS, TIME_LIMIT_SEC, 1_140_000L, activeSince, NOW);

        // then
        assertThat(time.elapsedActiveMs()).isEqualTo(1_260_000L);
        assertThat(time.remainingMs()).isZero();
    }

    @Test
    void calculate_제한시간정각_남은시간0() {

        // given & when
        RoomTime time = RoomTimeCalculator.calculate(RoomStatus.IN_PROGRESS, TIME_LIMIT_SEC, 1_199_999L, NOW.minusNanos(1_000_000), NOW);

        // then
        assertThat(time.remainingMs()).isZero();
    }

    @Test
    void toDurationSec_초단위내림() {

        // given & when & then
        assertThat(RoomTimeCalculator.toDurationSec(0L)).isZero();
        assertThat(RoomTimeCalculator.toDurationSec(999L)).isZero();
        assertThat(RoomTimeCalculator.toDurationSec(1_000L)).isEqualTo(1);
        assertThat(RoomTimeCalculator.toDurationSec(61_999L)).isEqualTo(61);
        assertThat(RoomTimeCalculator.toDurationSec(-5L)).isZero();
    }
}
