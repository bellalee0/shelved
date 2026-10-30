package com.shelved.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 성공 응답 메시지. 이름 규칙은 {도메인}_{행위}_SUCCESS
 * 도메인별 주석으로 묶어 각 도메인 담당이 추가한다.
 */
@Getter
@RequiredArgsConstructor
public enum SuccessMessage {

    ;

    private final String message;
}
