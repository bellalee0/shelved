package com.shelved.domain.hint.controller;

import static com.shelved.common.enums.SuccessMessage.HINT_CREATE_SUCCESS;

import com.shelved.common.dto.CommonResponse;
import com.shelved.common.jwt.AuthUser;
import com.shelved.domain.airequest.service.AiRequestResult;
import com.shelved.domain.hint.dto.HintCreateRequest;
import com.shelved.domain.hint.service.HintService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "힌트", description = "질문형 힌트")
@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class HintController {

    private static final String CONNECTION_ID_HEADER = "X-Connection-Id";

    private final HintService hintService;

    @Operation(summary = "새 힌트 요청", description = "X-Connection-Id 필수. 정상 답변이 저장되면 1회 사용. 200 HintCreateResponse, "
            + "같은 requestId가 처리 중이면 202 AiRequestStatusResponse. 남은 횟수 없음 409 HINT_LIMIT_EXCEEDED, "
            + "정답을 직접 묻는 질문 400 QUESTION_BLOCKED, AI 실패 502 HINT_GENERATION_FAILED (모두 차감 없음)")
    @PostMapping("/{roomId}/hints")
    public ResponseEntity<CommonResponse<Object>> createHint(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long roomId,
            @RequestHeader(value = CONNECTION_ID_HEADER, required = false) String connectionId,
            @Valid @RequestBody HintCreateRequest request
    ) {
        AiRequestResult result = hintService.createHint(authUser, roomId, request, connectionId);

        return result.toResponseEntity(HINT_CREATE_SUCCESS);
    }
}
