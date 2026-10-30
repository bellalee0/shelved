package com.shelved.domain.user.dto;

import static com.shelved.common.constants.ValidationMessage.NICKNAME_NOT_BLANK;
import static com.shelved.common.constants.ValidationMessage.NICKNAME_PATTERN;
import static com.shelved.common.constants.ValidationMessage.NICKNAME_SIZE;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "닉네임 설정·변경 요청")
public record UserNicknameUpdateRequest(
        @Schema(description = "2~12자, 완성형 한글·영문·숫자. 영문 대소문자를 구분하지 않고 중복 검사", example = "탐정7")
        @NotBlank(message = NICKNAME_NOT_BLANK)
        @Size(min = 2, max = 12, message = NICKNAME_SIZE)
        @Pattern(regexp = "^[가-힣A-Za-z0-9]*$", message = NICKNAME_PATTERN)
        String nickname
) {
}
