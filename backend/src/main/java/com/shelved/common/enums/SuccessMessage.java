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

    // 인증
    AUTH_LOGIN_SUCCESS("로그인 성공"),
    AUTH_TOKEN_REISSUE_SUCCESS("토큰 재발급 성공"),
    AUTH_LOGOUT_SUCCESS("로그아웃 성공"),

    // 사용자
    USER_ME_GET_SUCCESS("내 정보 조회 성공"),
    USER_NICKNAME_UPDATE_SUCCESS("닉네임 설정 성공"),

    ;

    private final String message;
}
