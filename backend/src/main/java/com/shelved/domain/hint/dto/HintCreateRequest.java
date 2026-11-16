package com.shelved.domain.hint.dto;

import static com.shelved.common.constants.ValidationMessage.QUESTION_NOT_BLANK;
import static com.shelved.common.constants.ValidationMessage.QUESTION_SIZE;
import static com.shelved.common.constants.ValidationMessage.REQUEST_ID_NOT_BLANK;
import static com.shelved.common.constants.ValidationMessage.REQUEST_ID_PATTERN;

import com.shelved.common.constants.ValidationMessage;
import com.shelved.common.utils.NoTrim;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param requestId 클라이언트가 만든 UUID. 재시도는 같은 값
 * @param question  원문 그대로 저장한다(@NoTrim). 공백만 있는 질문은 거부한다. 1~500자 (UTF-16 코드 단위)
 */
public record HintCreateRequest(
        @NotBlank(message = REQUEST_ID_NOT_BLANK)
        @Pattern(regexp = ValidationMessage.UUID_REGEX, message = REQUEST_ID_PATTERN)
        String requestId,

        @NoTrim
        @NotBlank(message = QUESTION_NOT_BLANK)
        @Size(max = 500, message = QUESTION_SIZE)
        String question
) {

    // 질문 원문이 로그에 섞이지 않도록 record 기본 toString을 대체한다
    @Override
    public String toString() {
        return "HintCreateRequest[requestId=" + requestId + "]";
    }
}
