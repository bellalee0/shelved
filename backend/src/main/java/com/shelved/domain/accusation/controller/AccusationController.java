package com.shelved.domain.accusation.controller;

import static com.shelved.common.enums.SuccessMessage.ACCUSATION_CREATE_SUCCESS;
import static com.shelved.common.enums.SuccessMessage.ACCUSATION_LIST_GET_SUCCESS;

import com.shelved.common.dto.CommonResponse;
import com.shelved.common.jwt.AuthUser;
import com.shelved.domain.accusation.dto.AccusationCreateRequest;
import com.shelved.domain.accusation.dto.AccusationListResponse;
import com.shelved.domain.accusation.service.AccusationService;
import com.shelved.domain.airequest.service.AiRequestResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "지목", description = "범인·트릭 지목 제출과 AI 채점")
@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class AccusationController {

    private static final String CONNECTION_ID_HEADER = "X-Connection-Id";

    private final AccusationService accusationService;

    @Operation(summary = "지목 기록", description = "지목 기록(오래된 순)과 accusationCount. 모든 세션 상태에서 조회")
    @GetMapping("/{roomId}/accusations")
    public ResponseEntity<CommonResponse<AccusationListResponse>> getAccusationList(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long roomId
    ) {
        AccusationListResponse response = accusationService.getAccusationList(authUser, roomId);

        return ResponseEntity.status(HttpStatus.OK)
                .body(CommonResponse.success(ACCUSATION_LIST_GET_SUCCESS, response));
    }

    @Operation(summary = "지목 제출·재채점", description = "새 답안은 X-Connection-Id 필수, 채점 실패한 requestId의 같은 답안 재채점은 헤더 불필요(마감 후에도 가능, 최소 10초 간격). "
            + "200 AccusationCreateResponse, 같은 requestId가 채점 중이면 202 AiRequestStatusResponse. "
            + "기회 없음 409 ACCUSATION_LIMIT_EXCEEDED, 마감 후 새 답안 409 ROOM_TIME_EXPIRED, 대체된 지목 재채점 409 AI_REQUEST_SUPERSEDED, "
            + "재채점 간격 전 429 GRADING_RETRY_TOO_SOON, 채점 실패 502 GRADING_FAILED (기회 미차감)")
    @PostMapping("/{roomId}/accusations")
    public ResponseEntity<CommonResponse<Object>> createAccusation(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long roomId,
            @RequestHeader(value = CONNECTION_ID_HEADER, required = false) String connectionId,
            @Valid @RequestBody AccusationCreateRequest request
    ) {
        AiRequestResult result = accusationService.createAccusation(authUser, roomId, request, connectionId);

        return result.toResponseEntity(ACCUSATION_CREATE_SUCCESS);
    }
}
