package com.shelved.domain.accusation.dto;

import static com.shelved.common.constants.ValidationMessage.REQUEST_ID_NOT_BLANK;
import static com.shelved.common.constants.ValidationMessage.REQUEST_ID_PATTERN;
import static com.shelved.common.constants.ValidationMessage.SUSPECT_ID_NOT_NULL;
import static com.shelved.common.constants.ValidationMessage.TRICK_EXPLANATION_NOT_BLANK;
import static com.shelved.common.constants.ValidationMessage.TRICK_EXPLANATION_SIZE;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.shelved.common.constants.ValidationMessage;
import com.shelved.common.utils.NoTrim;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 지목 제출·재채점 요청 (docs/api/hint-accusation.md). 재채점은 같은 requestId·같은 suspectId·같은 trickExplanation
 *
 * @param requestId        클라이언트가 만든 UUID
 * @param suspectId        이 사건의 인물
 * @param trickExplanation 원문 그대로 저장한다(@NoTrim). 앞뒤 공백을 뺀 길이가 10~3000자 (UTF-16 코드 단위, 화면의 text.trim().length와 같다)
 */
public record AccusationCreateRequest(
        @NotBlank(message = REQUEST_ID_NOT_BLANK)
        @Pattern(regexp = ValidationMessage.UUID_REGEX, message = REQUEST_ID_PATTERN)
        String requestId,

        @NotNull(message = SUSPECT_ID_NOT_NULL)
        Long suspectId,

        @NoTrim
        @NotBlank(message = TRICK_EXPLANATION_NOT_BLANK)
        String trickExplanation
) {

    public static final int TRICK_EXPLANATION_MIN_LENGTH = 10;
    public static final int TRICK_EXPLANATION_MAX_LENGTH = 3000;

    /**
     * 앞뒤 공백을 뺀 길이 검사. 비었으면 @NotBlank가 처리한다
     */
    @JsonIgnore
    @AssertTrue(message = TRICK_EXPLANATION_SIZE)
    public boolean isTrickExplanationLengthValid() {
        if (trickExplanation == null || trickExplanation.isBlank()) {
            return true;
        }
        int length = trimmedLength(trickExplanation);
        return length >= TRICK_EXPLANATION_MIN_LENGTH && length <= TRICK_EXPLANATION_MAX_LENGTH;
    }

    /**
     * JavaScript String.prototype.trim()과 같은 공백 기준으로 앞뒤를 뺀 길이.
     * Java의 strip()은 줄바꿈 없는 공백(U+00A0 등)·U+FEFF를 남기고 U+001C~U+001F를 지우므로 화면 기준과 어긋난다
     */
    static int trimmedLength(String text) {
        int start = 0;
        int end = text.length();
        while (start < end && isJsWhitespace(text.charAt(start))) {
            start++;
        }
        while (end > start && isJsWhitespace(text.charAt(end - 1))) {
            end--;
        }
        return end - start;
    }

    // ECMAScript WhiteSpace + LineTerminator
    private static boolean isJsWhitespace(char c) {
        return c == '\t' || c == '\u000B' || c == '\f' || c == '﻿'
                || c == '\n' || c == '\r' || c == ' ' || c == ' '
                || Character.getType(c) == Character.SPACE_SEPARATOR;
    }

    // 답안 원문이 로그에 섞이지 않도록 record 기본 toString을 대체한다
    @Override
    public String toString() {
        return "AccusationCreateRequest[requestId=" + requestId + ", suspectId=" + suspectId + "]";
    }
}
