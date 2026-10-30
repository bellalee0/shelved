package com.shelved.domain.auth.dto;

import static com.shelved.common.constants.ValidationMessage.REFRESH_TOKEN_NOT_BLANK;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "로그아웃 요청")
public record AuthLogoutRequest(
        @Schema(description = "로그인·재발급 때 받은 리프레시 토큰")
        @NotBlank(message = REFRESH_TOKEN_NOT_BLANK)
        String refreshToken
) {
}
