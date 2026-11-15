package com.shelved.domain.airequest.controller;

import static com.shelved.common.enums.SuccessMessage.AI_REQUEST_GET_SUCCESS;

import com.shelved.common.dto.CommonResponse;
import com.shelved.common.jwt.AuthUser;
import com.shelved.domain.airequest.dto.AiRequestStatusResponse;
import com.shelved.domain.airequest.service.AiRequestQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI 요청", description = "NPC 질문·힌트·보충 질문·지목 AI 요청의 처리 상태")
@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class AiRequestController {

    private final AiRequestQueryService aiRequestQueryService;

    @Operation(summary = "AI 요청 상태", description = "202를 받았거나 응답 없이 연결이 끊긴 요청의 결과 확인. 일시정지·종료 후에도 조회할 수 있다. 없는 요청은 404 AI_REQUEST_NOT_FOUND")
    @GetMapping("/{roomId}/ai-requests/{requestId}")
    public ResponseEntity<CommonResponse<AiRequestStatusResponse>> getAiRequest(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long roomId,
            @PathVariable String requestId
    ) {
        AiRequestStatusResponse response = aiRequestQueryService.getAiRequest(authUser, roomId, requestId);

        return ResponseEntity.status(HttpStatus.OK)
                .body(CommonResponse.success(AI_REQUEST_GET_SUCCESS, response));
    }
}
