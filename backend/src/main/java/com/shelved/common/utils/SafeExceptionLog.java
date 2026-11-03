package com.shelved.common.utils;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 예외를 메시지 없이 로그용 문자열로 바꾼다 (common.md 비밀 값 및 로그 관리).
 * 예외 메시지에는 DB 제약 위반 값·요청 값 등이 들어갈 수 있으므로 클래스 이름과 스택 위치만 남긴다.
 * 예외 객체를 로거에 그대로 넘기면 메시지가 출력되므로, 예외를 로그에 남길 때는 이 클래스를 거친다.
 *
 * <pre>
 * log.error("... exception={}{}", SafeExceptionLog.causeChain(e), SafeExceptionLog.rootCauseFrames(e));
 * </pre>
 */
public final class SafeExceptionLog {

    public static final int DEFAULT_FRAME_LIMIT = 10;

    private SafeExceptionLog() {
    }

    // 예: org.springframework.dao.DataIntegrityViolationException <- org.postgresql.util.PSQLException
    public static String causeChain(Throwable throwable) {
        return causes(throwable)
                .map(cause -> cause.getClass().getName())
                .collect(Collectors.joining(" <- "));
    }

    public static String rootCauseFrames(Throwable throwable) {
        return rootCauseFrames(throwable, DEFAULT_FRAME_LIMIT);
    }

    // 근본 원인의 상위 스택 프레임. 각 줄은 줄바꿈 + "\tat "로 시작한다 (메시지 없음)
    public static String rootCauseFrames(Throwable throwable, int limit) {
        if (throwable == null) {
            return "";
        }

        Throwable root = causes(throwable).reduce((first, second) -> second).orElse(throwable);

        return Stream.of(root.getStackTrace())
                .limit(limit)
                .map(frame -> System.lineSeparator() + "\tat " + frame)
                .collect(Collectors.joining());
    }

    // 원인 체인. 순환 참조가 있어도 같은 예외를 두 번 지나지 않는다
    private static Stream<Throwable> causes(Throwable throwable) {
        if (throwable == null) {
            return Stream.empty();
        }

        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Stream.Builder<Throwable> builder = Stream.builder();
        for (Throwable current = throwable; current != null && visited.add(current); current = current.getCause()) {
            builder.add(current);
        }

        return builder.build();
    }
}
