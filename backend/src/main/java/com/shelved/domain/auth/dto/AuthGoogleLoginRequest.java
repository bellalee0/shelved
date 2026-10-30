package com.shelved.domain.auth.dto;

import static com.shelved.common.constants.ValidationMessage.ID_TOKEN_NOT_BLANK;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Google 로그인 요청")
public record AuthGoogleLoginRequest(
        @Schema(description = "Google Identity Services가 반환한 ID 토큰(credential)")
        @NotBlank(message = ID_TOKEN_NOT_BLANK)
        String idToken
) {
}
