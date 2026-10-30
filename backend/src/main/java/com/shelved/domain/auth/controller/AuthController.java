package com.shelved.domain.auth.controller;

import static com.shelved.common.enums.SuccessMessage.AUTH_LOGIN_SUCCESS;
import static com.shelved.common.enums.SuccessMessage.AUTH_LOGOUT_SUCCESS;
import static com.shelved.common.enums.SuccessMessage.AUTH_TOKEN_REISSUE_SUCCESS;

import com.shelved.common.dto.CommonResponse;
import com.shelved.domain.auth.dto.AuthGoogleLoginRequest;
import com.shelved.domain.auth.dto.AuthLogoutRequest;
import com.shelved.domain.auth.dto.AuthRefreshRequest;
import com.shelved.domain.auth.dto.AuthTokenResponse;
import com.shelved.domain.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "인증", description = "Google 로그인, 토큰 재발급, 로그아웃")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Google 로그인", description = "Google ID 토큰을 검증하고 액세스·리프레시 토큰을 발급한다. 처음 로그인한 사용자는 닉네임 없이 생성된다.")
    @PostMapping("/google")
    public ResponseEntity<CommonResponse<AuthTokenResponse>> loginWithGoogle(@Valid @RequestBody AuthGoogleLoginRequest request) {

        AuthTokenResponse response = authService.loginWithGoogle(request);

        return ResponseEntity.status(HttpStatus.OK)
                .body(CommonResponse.success(AUTH_LOGIN_SUCCESS, response));
    }

    @Operation(summary = "토큰 재발급", description = "리프레시 토큰으로 액세스·리프레시 토큰을 새로 발급하고 이전 리프레시 토큰을 무효화한다.")
    @PostMapping("/refresh")
    public ResponseEntity<CommonResponse<AuthTokenResponse>> reissueToken(@Valid @RequestBody AuthRefreshRequest request) {

        AuthTokenResponse response = authService.reissueToken(request);

        return ResponseEntity.status(HttpStatus.OK)
                .body(CommonResponse.success(AUTH_TOKEN_REISSUE_SUCCESS, response));
    }

    @Operation(summary = "로그아웃", description = "리프레시 토큰을 무효화한다. Authorization 헤더가 필요 없고, 이미 무효이거나 없는 토큰이어도 성공한다.")
    @PostMapping("/logout")
    public ResponseEntity<CommonResponse<Void>> logout(@Valid @RequestBody AuthLogoutRequest request) {

        authService.logout(request);

        return ResponseEntity.status(HttpStatus.OK)
                .body(CommonResponse.successNodata(AUTH_LOGOUT_SUCCESS));
    }
}
