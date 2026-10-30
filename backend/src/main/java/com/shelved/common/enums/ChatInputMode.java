package com.shelved.common.enums;

/**
 * 제작 채팅 AI 입력 구성 방식: 원문·대화 이력 전체 / 오래된 대화 이력 요약 / 원문 분할
 */
public enum ChatInputMode {
    FULL,
    HISTORY_SUMMARIZED,
    SOURCE_CHUNKED
}
