package com.shelved.common.config;

import com.shelved.common.enums.Difficulty;
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
        Duration gradingRetryMinInterval
) {

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
