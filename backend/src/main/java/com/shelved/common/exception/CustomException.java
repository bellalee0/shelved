package com.shelved.common.exception;

import lombok.Getter;

/**
 * 비즈니스 예외. 도메인별 예외 클래스를 따로 만들지 않고 ErrorCode로 구분한다.
 * detail 형식은 docs/api/errors.md에 코드별로 정의한다.
 */
@Getter
public class CustomException extends RuntimeException {

    private final ErrorCode errorCode;
    private final transient Object detail;

    public CustomException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    public CustomException(ErrorCode errorCode, Object detail) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.detail = detail;
    }
}
