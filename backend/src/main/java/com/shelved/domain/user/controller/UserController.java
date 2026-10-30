package com.shelved.domain.user.controller;

import static com.shelved.common.enums.SuccessMessage.USER_ME_GET_SUCCESS;
import static com.shelved.common.enums.SuccessMessage.USER_NICKNAME_UPDATE_SUCCESS;

import com.shelved.common.dto.CommonResponse;
import com.shelved.common.jwt.AuthUser;
import com.shelved.domain.user.dto.UserMeResponse;
import com.shelved.domain.user.dto.UserNicknameUpdateRequest;
import com.shelved.domain.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "사용자", description = "내 정보, 닉네임")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "내 정보 조회", description = "새로고침 후 로그인 상태 복원에 사용한다. 닉네임이 없어도 호출할 수 있다.")
    @GetMapping("/me")
    public ResponseEntity<CommonResponse<UserMeResponse>> getMyInfo(@AuthenticationPrincipal AuthUser authUser) {

        UserMeResponse response = userService.getMyInfo(authUser.getId());

        return ResponseEntity.status(HttpStatus.OK)
                .body(CommonResponse.success(USER_ME_GET_SUCCESS, response));
    }

    @Operation(summary = "닉네임 설정·변경", description = "최초 설정과 변경에 같은 API를 사용한다. 새 토큰을 발급하지 않으며, 설정 직후 기존 토큰으로 다른 API를 호출할 수 있다.")
    @PatchMapping("/me/nickname")
    public ResponseEntity<CommonResponse<UserMeResponse>> updateNickname(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody UserNicknameUpdateRequest request
    ) {
        UserMeResponse response = userService.updateNickname(authUser.getId(), request);

        return ResponseEntity.status(HttpStatus.OK)
                .body(CommonResponse.success(USER_NICKNAME_UPDATE_SUCCESS, response));
    }
}
