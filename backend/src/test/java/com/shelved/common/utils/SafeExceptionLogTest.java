package com.shelved.common.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SafeExceptionLogTest {

    private static final String MARKER = "SENSITIVE-MARKER-1234";

    @Test
    void causeChain_원인체인_클래스이름만연결() {

        // given
        Exception e = new IllegalStateException(MARKER, new IllegalArgumentException(MARKER));

        // when
        String chain = SafeExceptionLog.causeChain(e);

        // then
        assertThat(chain).isEqualTo("java.lang.IllegalStateException <- java.lang.IllegalArgumentException");
    }

    @Test
    void rootCauseFrames_근본원인프레임_메시지없이개수제한() {

        // given
        Exception e = new IllegalStateException(MARKER, new IllegalArgumentException(MARKER));

        // when
        String frames = SafeExceptionLog.rootCauseFrames(e, 3);

        // then
        assertThat(frames).doesNotContain(MARKER).contains("\tat " + SafeExceptionLogTest.class.getName());
        assertThat(frames.lines().filter(line -> line.startsWith("\tat ")).count()).isEqualTo(3);
    }

    @Test
    void causeChain_순환원인_무한반복없음() {

        // given
        Exception a = new IllegalStateException(MARKER);
        Exception b = new IllegalArgumentException(MARKER, a);
        a.initCause(b);

        // when
        String chain = SafeExceptionLog.causeChain(a);

        // then
        assertThat(chain).isEqualTo("java.lang.IllegalStateException <- java.lang.IllegalArgumentException");
    }
}
