package com.shelved.domain.auth.dto;

import com.shelved.domain.user.dto.UserMeResponse;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "로그인·재발급 결과")
public record AuthTokenResponse(
        @Schema(description = "액세스 토큰. Authorization: Bearer {accessToken} 헤더로 보낸다")
        String accessToken,

        @Schema(description = "리프레시 토큰. 재발급할 때마다 새 값으로 바뀐다")
        String refreshToken,

        @Schema(description = "액세스 토큰 유효기간(초)", example = "3600")
        long accessTokenExpiresInSec,

        @Schema(description = "로그인한 사용자 정보")
        UserMeResponse user
) {
}
