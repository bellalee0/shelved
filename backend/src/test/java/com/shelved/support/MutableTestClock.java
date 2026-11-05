package com.shelved.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 테스트용 조정 가능한 Clock. 고정하지 않으면 시스템 시각을 따른다 (다른 통합 테스트의 동작을 바꾸지 않도록).
 * 통합 테스트는 IntegrationTestSupport가 매 테스트 후 reset()한다
 */
public class MutableTestClock extends Clock {

    private final ZoneId zone;
    private volatile Instant fixedInstant;

    public MutableTestClock(ZoneId zone) {
        this.zone = zone;
    }

    public void fixAt(LocalDateTime localDateTime) {
        this.fixedInstant = localDateTime.atZone(zone).toInstant();
    }

    public void advance(Duration duration) {
        if (fixedInstant == null) {
            throw new IllegalStateException("fixAt으로 먼저 고정해야 합니다.");
        }
        this.fixedInstant = fixedInstant.plus(duration);
    }

    public void reset() {
        this.fixedInstant = null;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Instant instant() {
        Instant fixed = fixedInstant;
        return fixed != null ? fixed : Instant.now();
    }
}
