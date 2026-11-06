package com.shelved.domain.note.dto;

import java.time.LocalDateTime;

/**
 * @param maxLength 최대 글자 수 (서버 설정값, UTF-16 코드 단위)
 * @param editable  IN_PROGRESS이고 서버 기준 남은 시간이 있을 때만 true
 */
public record NoteResponse(
        String content,
        int revision,
        LocalDateTime updatedAt,
        int maxLength,
        boolean editable
) {
}
