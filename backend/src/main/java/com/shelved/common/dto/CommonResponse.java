package com.shelved.common.dto;

import com.shelved.common.enums.SuccessMessage;
import com.shelved.common.exception.ErrorCode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 모든 API 응답의 공통 형식 (docs/api/README.md 공통 응답 형식)
 */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class CommonResponse<T> {

    private final boolean success;
    private final String message;
    private final T data;
    private final LocalDateTime timestamp;

    // 성공 시 (사용 예시: CommonResponse.success(SuccessMessage 이넘, 반환 DTO))
    public static <T> CommonResponse<T> success(SuccessMessage successMessage, T data) {
        return new CommonResponse<>(true, successMessage.getMessage(), data, now());
    }

    // 성공했는데 응답 데이터는 없을 시 (사용 예시: CommonResponse.successNodata(SuccessMessage 이넘))
    public static CommonResponse<Void> successNodata(SuccessMessage successMessage) {
        return new CommonResponse<>(true, successMessage.getMessage(), null, now());
    }

    // 예외 처리 시 (전역 예외 핸들러 및 인증·인가 오류 처리기에서 사용)
    public static CommonResponse<ErrorData> exception(ErrorCode errorCode) {
        return exception(errorCode, errorCode.getMessage(), null);
    }

    public static CommonResponse<ErrorData> exception(ErrorCode errorCode, Object detail) {
        return exception(errorCode, errorCode.getMessage(), detail);
    }

    // ErrorCode 기본 메시지 대신 다른 메시지를 보여줄 때 (예: INVALID_INPUT의 검증 메시지)
    public static CommonResponse<ErrorData> exception(ErrorCode errorCode, String message, Object detail) {
        return new CommonResponse<>(false, message, new ErrorData(errorCode.name(), detail), now());
    }

    // 응답 생성 시각 (판정과 무관하므로 Clock을 쓰지 않는다, backend.md 12)
    private static LocalDateTime now() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
    }
}
