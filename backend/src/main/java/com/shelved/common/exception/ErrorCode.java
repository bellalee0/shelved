package com.shelved.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 예외 코드. docs/api/errors.md와 1:1로 대응한다.
 * 코드 이름과 상태 코드를 바꾸면 Breaking Change이므로 문서를 먼저 고친다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 공통
    // 실제 응답 메시지는 @Valid 검증 메시지 중 첫 번째. 아래는 메시지를 얻지 못했을 때의 기본값
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    INVALID_REQUEST_BODY(HttpStatus.BAD_REQUEST, "요청 본문 형식이 올바르지 않습니다."),
    INVALID_PARAMETER(HttpStatus.BAD_REQUEST, "파라미터 값이 올바르지 않습니다."),
    API_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 API를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),

    // 인증 · 사용자
    LOGIN_REQUIRED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "로그인이 만료되었습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "로그인 정보가 만료되었습니다. 다시 로그인해 주세요."),
    GOOGLE_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Google 로그인 정보를 확인할 수 없습니다. 다시 시도해 주세요."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    NICKNAME_REQUIRED(HttpStatus.FORBIDDEN, "닉네임을 먼저 설정해 주세요."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자가 존재하지 않습니다."),
    NICKNAME_EXIST(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),

    // 사건 · 랭킹
    CASE_NOT_FOUND(HttpStatus.NOT_FOUND, "사건이 존재하지 않습니다."),

    // 세션
    CONNECTION_ID_REQUIRED(HttpStatus.BAD_REQUEST, "수사 연결 정보가 없습니다. 게임 화면을 다시 열어 주세요."),
    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "세션이 존재하지 않습니다."),
    ROOM_ALREADY_EXISTS(HttpStatus.CONFLICT, "이 사건에 진행 중인 수사가 있습니다. 이어서 진행해 주세요."),
    ROOM_NOT_READY(HttpStatus.CONFLICT, "이미 시작된 수사입니다."),
    ROOM_NOT_PAUSED(HttpStatus.CONFLICT, "일시정지 상태의 수사만 이어할 수 있습니다."),
    ROOM_NOT_IN_PROGRESS(HttpStatus.CONFLICT, "진행 중인 수사가 아닙니다."),
    ROOM_TIME_EXPIRED(HttpStatus.CONFLICT, "제한시간이 끝났습니다."),
    ROOM_NOT_ENDED(HttpStatus.CONFLICT, "아직 종료되지 않은 수사입니다."),
    CONNECTION_REPLACED(HttpStatus.CONFLICT, "다른 화면에서 수사를 이어하고 있습니다."),
    ROOM_ENDED_DURING_REQUEST(HttpStatus.CONFLICT, "수사가 종료되어 이 요청은 반영되지 않았습니다."),

    // AI 요청 공통
    AI_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "요청을 찾을 수 없습니다."),
    AI_REQUEST_ID_REUSED(HttpStatus.CONFLICT, "같은 요청 식별자로 다른 내용을 보낼 수 없습니다."),
    AI_REQUEST_IN_PROGRESS(HttpStatus.CONFLICT, "이전 요청을 처리하고 있습니다. 잠시 후 다시 시도해 주세요."),
    AI_REQUEST_SUPERSEDED(HttpStatus.CONFLICT, "새 답안으로 대체된 제출은 다시 채점할 수 없습니다."),

    // NPC · 물증 · 노트
    QUESTION_BLOCKED(HttpStatus.BAD_REQUEST, "범인이나 정답을 직접 묻는 질문에는 답할 수 없습니다. 인물의 행적, 목격한 사실, 물증에 관해 질문해 보세요."),
    NPC_NOT_CONVERSABLE(HttpStatus.BAD_REQUEST, "대화할 수 없는 인물입니다."),
    SUSPECT_NOT_FOUND(HttpStatus.NOT_FOUND, "인물이 존재하지 않습니다."),
    EVIDENCE_NOT_FOUND(HttpStatus.NOT_FOUND, "물증이 존재하지 않습니다."),
    NOTE_REVISION_CONFLICT(HttpStatus.CONFLICT, "다른 곳에서 노트가 먼저 저장되었습니다. 서버 내용과 작성 중인 내용을 확인해 주세요."),
    NPC_ANSWER_FAILED(HttpStatus.BAD_GATEWAY, "NPC의 답변을 받지 못했습니다. 같은 질문을 다시 보내 주세요."),

    // 힌트 · 지목
    HINT_NOT_FOUND(HttpStatus.NOT_FOUND, "힌트가 존재하지 않습니다."),
    HINT_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "남은 힌트 횟수가 없습니다."),
    HINT_FOLLOWUP_ALREADY_USED(HttpStatus.CONFLICT, "이 힌트의 보충 질문을 이미 사용했습니다."),
    ACCUSATION_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "남은 지목 기회가 없습니다."),
    GRADING_RETRY_TOO_SOON(HttpStatus.TOO_MANY_REQUESTS, "잠시 후 다시 채점할 수 있습니다."),
    HINT_GENERATION_FAILED(HttpStatus.BAD_GATEWAY, "힌트를 받지 못했습니다. 힌트 횟수는 차감되지 않았습니다."),
    HINT_FOLLOWUP_FAILED(HttpStatus.BAD_GATEWAY, "보충 설명을 받지 못했습니다. 보충 질문 기회는 그대로 남아 있습니다."),
    GRADING_FAILED(HttpStatus.BAD_GATEWAY, "채점 중 오류가 발생했습니다. 지목 기회는 차감되지 않았으며 같은 답안으로 다시 채점할 수 있습니다."),

    // 관리자 사건 제작
    SOURCE_TEXT_INVALID_FILE(HttpStatus.BAD_REQUEST, "UTF-8 인코딩의 .txt 파일만 업로드할 수 있습니다."),
    COVER_IMAGE_INVALID_FILE(HttpStatus.BAD_REQUEST, "jpg, png, webp 이미지만 업로드할 수 있습니다."),
    FILE_TOO_LARGE(HttpStatus.BAD_REQUEST, "파일 크기가 허용 범위를 넘었습니다."),
    LLM_INPUT_TOO_LARGE(HttpStatus.BAD_REQUEST, "원문과 대화 내용이 AI 입력 한도를 넘어 처리할 수 없습니다. 원문 분할 처리가 필요합니다."),
    MAP_INVALID(HttpStatus.BAD_REQUEST, "맵 설정을 확인해 주세요."),
    HOTSPOT_INVALID(HttpStatus.BAD_REQUEST, "배치 좌표를 확인해 주세요."),
    TEST_ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "테스트 세션이 존재하지 않습니다."),
    CASE_PUBLISHED_LOCKED(HttpStatus.CONFLICT, "공개된 사건은 수정할 수 없습니다. 초안으로 복제해 수정해 주세요."),
    CASE_NOT_DRAFT(HttpStatus.CONFLICT, "초안 상태에서만 할 수 있습니다. 수정 재개 후 다시 시도해 주세요."),
    CASE_NOT_CONTENT_CONFIRMED(HttpStatus.CONFLICT, "콘텐츠 확정 상태에서만 할 수 있습니다."),
    CASE_NOT_PUBLISHED(HttpStatus.CONFLICT, "공개된 사건만 복제할 수 있습니다."),
    CASE_CHAT_IN_PROGRESS(HttpStatus.CONFLICT, "제작 채팅을 처리하고 있습니다. 완료된 뒤 다시 시도해 주세요."),
    CASE_CHAT_CONTEXT_CHANGED(HttpStatus.CONFLICT, "제작 내용이 변경되었습니다. 최신 내용을 확인한 뒤 새 메시지로 보내 주세요."),
    CASE_AI_REQUEST_IN_PROGRESS(HttpStatus.CONFLICT, "테스트 세션의 AI 요청을 처리하고 있습니다. 잠시 후 다시 시도해 주세요."),
    SOURCE_TEXT_REQUIRED(HttpStatus.CONFLICT, "원문을 먼저 업로드해 주세요."),
    DRAFT_INVALID(HttpStatus.CONFLICT, "초안에 수정할 항목이 있습니다."),
    MAP_NOT_CONFIGURED(HttpStatus.CONFLICT, "맵 설정을 먼저 저장해 주세요."),
    TEST_ROOM_UNFINISHED(HttpStatus.CONFLICT, "종료되지 않은 테스트 세션이 있습니다. 테스트를 완료하거나 중단해 주세요."),
    TEST_ROOM_ALREADY_ENDED(HttpStatus.CONFLICT, "이미 종료된 테스트 세션입니다."),
    TEST_NOT_COMPLETED(HttpStatus.CONFLICT, "현재 내용으로 완료한 테스트가 없습니다."),
    PUBLISH_CHECK_FAILED(HttpStatus.CONFLICT, "출시 전 확인할 항목이 있습니다."),
    FILE_STORAGE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "파일 저장 중 오류가 발생했습니다."),
    ASSET_MANIFEST_INVALID(HttpStatus.INTERNAL_SERVER_ERROR, "에셋 목록 파일을 읽을 수 없습니다."),
    CASE_CHAT_FAILED(HttpStatus.BAD_GATEWAY, "AI 응답을 받지 못했습니다. 초안은 변경되지 않았으며 같은 메시지로 다시 시도할 수 있습니다."),

    ;

    private final HttpStatus status;
    private final String message;
}
