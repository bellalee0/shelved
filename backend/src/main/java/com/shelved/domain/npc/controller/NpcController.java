package com.shelved.domain.npc.controller;

import static com.shelved.common.enums.SuccessMessage.NPC_ASK_SUCCESS;
import static com.shelved.common.enums.SuccessMessage.NPC_MESSAGE_LIST_GET_SUCCESS;

import com.shelved.common.dto.CommonResponse;
import com.shelved.common.jwt.AuthUser;
import com.shelved.domain.airequest.service.AiRequestResult;
import com.shelved.domain.npc.dto.NpcAskRequest;
import com.shelved.domain.npc.dto.NpcMessageListResponse;
import com.shelved.domain.npc.service.NpcService;
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

@Tag(name = "NPC", description = "NPC 자유 질문과 대화 기록")
@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class NpcController {

    private static final String CONNECTION_ID_HEADER = "X-Connection-Id";

    private final NpcService npcService;

    @Operation(summary = "NPC 질문", description = "X-Connection-Id 필수. 200 NpcAskResponse, 같은 requestId가 처리 중이면 202 AiRequestStatusResponse. "
            + "범인 정체·범행 여부를 묻는 질문은 접수 전에 400 QUESTION_BLOCKED, AI 실패는 502 NPC_ANSWER_FAILED")
    @PostMapping("/{roomId}/npc/{suspectId}/ask")
    public ResponseEntity<CommonResponse<Object>> askNpc(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long roomId,
            @PathVariable Long suspectId,
            @RequestHeader(value = CONNECTION_ID_HEADER, required = false) String connectionId,
            @Valid @RequestBody NpcAskRequest request
    ) {
        AiRequestResult result = npcService.askNpc(authUser, roomId, suspectId, request, connectionId);

        return result.toResponseEntity(NPC_ASK_SUCCESS);
    }

    @Operation(summary = "NPC 대화 기록", description = "sequenceNo 순 원문과, 처리 중·재시도 대기 중인 질문(pendingRequests). 모든 세션 상태에서 조회")
    @GetMapping("/{roomId}/npc/{suspectId}/messages")
    public ResponseEntity<CommonResponse<NpcMessageListResponse>> getNpcMessageList(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long roomId,
            @PathVariable Long suspectId
    ) {
        NpcMessageListResponse response = npcService.getNpcMessageList(authUser, roomId, suspectId);

        return ResponseEntity.status(HttpStatus.OK)
                .body(CommonResponse.success(NPC_MESSAGE_LIST_GET_SUCCESS, response));
    }
}
