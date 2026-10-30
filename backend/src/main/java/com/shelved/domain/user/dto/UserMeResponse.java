package com.shelved.domain.user.dto;

import com.shelved.common.entity.User;
import com.shelved.common.enums.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "내 정보")
public record UserMeResponse(
        @Schema(description = "사용자 ID", example = "7")
        Long userId,

        @Schema(description = "닉네임. 미설정 시 null", example = "탐정7", nullable = true)
        String nickname,

        @Schema(description = "역할", example = "PLAYER")
        UserRole role,

        @Schema(description = "닉네임 설정이 필요한지 (nickname == null)", example = "false")
        boolean nicknameRequired
) {

    public static UserMeResponse from(User user) {
        return new UserMeResponse(
                user.getId(),
                user.getNickname(),
                user.getRole(),
                user.getNickname() == null
        );
    }
}
