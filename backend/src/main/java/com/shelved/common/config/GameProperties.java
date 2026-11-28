package com.shelved.common.config;

import com.shelved.common.enums.Difficulty;
import java.math.BigDecimal;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 게임 규칙 설정값 (shelved.game). 값은 잠정치이며 application.yml에서 조정한다.
 */
@ConfigurationProperties(prefix = "shelved.game")
public record GameProperties(
        TimeLimit timeLimit,
        HintLimit hintLimit,
        int accusationLimit,
        Duration heartbeatInterval,
        Duration heartbeatTimeout,
        Duration gradingRetryMinInterval,
        Duration disconnectCheckInterval,
        Grading grading
) {

    public GameProperties {
        if (grading == null) {
            throw new IllegalArgumentException("shelved.game.grading.element-pass-ratio 설정이 필요합니다.");
        }
    }

    /**
     * 난이도의 제한시간
     */
    public Duration timeLimitOf(Difficulty difficulty) {
        return switch (difficulty) {
            case EASY -> timeLimit.easy();
            case NORMAL -> timeLimit.normal();
            case HARD -> timeLimit.hard();
        };
    }

    /**
     * 난이도의 새 힌트 횟수
     */
    public int hintLimitOf(Difficulty difficulty) {
        return switch (difficulty) {
            case EASY -> hintLimit.easy();
            case NORMAL -> hintLimit.normal();
            case HARD -> hintLimit.hard();
        };
    }

    /**
     * 지목 판정 규칙
     *
     * @param elementPassRatio 핵심 요소 통과에 필요한 충족(MET) 항목 비율 (0 초과 1 이하). 필요 항목 수는 올림한다
     */
    public record Grading(
            BigDecimal elementPassRatio
    ) {

        public Grading {
            if (elementPassRatio == null
                    || elementPassRatio.signum() <= 0
                    || elementPassRatio.compareTo(BigDecimal.ONE) > 0) {
                throw new IllegalArgumentException("shelved.game.grading.element-pass-ratio는 0 초과 1 이하여야 합니다.");
            }
        }
    }

    /** 난이도별 제한시간 */
    public record TimeLimit(
            Duration easy,
            Duration normal,
            Duration hard
    ) {
    }

    /** 난이도별 힌트 횟수 */
    public record HintLimit(
            int easy,
            int normal,
            int hard
    ) {
    }
}
