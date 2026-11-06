package com.shelved.domain.note.dto;

import static com.shelved.common.constants.ValidationMessage.NOTE_CONTENT_NOT_NULL;
import static com.shelved.common.constants.ValidationMessage.NOTE_REVISION_NOT_NULL;
import static com.shelved.common.constants.ValidationMessage.NOTE_REVISION_POSITIVE_OR_ZERO;

import com.shelved.common.utils.NoTrim;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * @param content  원문 그대로 저장한다(@NoTrim: 공통 앞뒤 공백 제거 제외). 빈 문자열 허용. 최대 글자 수는 설정값이라 서비스에서 검사한다
 * @param revision 마지막으로 받은 revision
 */
public record NoteUpdateRequest(
        @NoTrim
        @NotNull(message = NOTE_CONTENT_NOT_NULL)
        String content,

        @NotNull(message = NOTE_REVISION_NOT_NULL)
        @PositiveOrZero(message = NOTE_REVISION_POSITIVE_OR_ZERO)
        Integer revision
) {
}
