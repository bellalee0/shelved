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

    // 사건
    CASE_LIST_GET_SUCCESS("사건 목록 조회 성공"),

    // 세션
    ROOM_LIST_GET_SUCCESS("세션 목록 조회 성공"),
    ROOM_CREATE_SUCCESS("세션 생성 성공"),
    ROOM_BRIEFING_GET_SUCCESS("사전 정보 조회 성공"),
    ROOM_MAP_GET_SUCCESS("맵 정보 조회 성공"),
    ROOM_STATE_GET_SUCCESS("세션 상태 조회 성공"),
    ROOM_START_SUCCESS("수사 시작 성공"),
    ROOM_HEARTBEAT_SUCCESS("접속 확인 성공"),
    ROOM_PAUSE_SUCCESS("일시정지 성공"),
    ROOM_RESUME_SUCCESS("수사 이어하기 성공"),

    ;

    private final String message;
}
