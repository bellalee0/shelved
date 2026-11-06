package com.shelved.domain.note.controller;

import static com.shelved.common.enums.SuccessMessage.NOTE_GET_SUCCESS;
import static com.shelved.common.enums.SuccessMessage.NOTE_UPDATE_SUCCESS;

import com.shelved.common.dto.CommonResponse;
import com.shelved.common.jwt.AuthUser;
import com.shelved.domain.note.dto.NoteResponse;
import com.shelved.domain.note.dto.NoteUpdateRequest;
import com.shelved.domain.note.service.NoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "개인 노트", description = "내 개인 노트 조회·저장")
@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class NoteController {

    private static final String CONNECTION_ID_HEADER = "X-Connection-Id";

    private final NoteService noteService;

    @Operation(summary = "내 노트 조회", description = "모든 상태에서 조회 가능, 연결 헤더 불필요. editable은 IN_PROGRESS이고 시간이 남아 있을 때만 true")
    @GetMapping("/{roomId}/notes/me")
    public ResponseEntity<CommonResponse<NoteResponse>> getMyNote(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long roomId
    ) {
        NoteResponse response = noteService.getMyNote(authUser, roomId);

        return ResponseEntity.status(HttpStatus.OK)
                .body(CommonResponse.success(NOTE_GET_SUCCESS, response));
    }

    @Operation(summary = "내 노트 저장", description = "X-Connection-Id 필수. content는 원문 그대로 저장. revision 불일치 시 409 NOTE_REVISION_CONFLICT(detail.currentRevision, currentContent)")
    @PutMapping("/{roomId}/notes/me")
    public ResponseEntity<CommonResponse<NoteResponse>> updateMyNote(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long roomId,
            @RequestHeader(value = CONNECTION_ID_HEADER, required = false) String connectionId,
            @Valid @RequestBody NoteUpdateRequest request
    ) {
        NoteResponse response = noteService.updateMyNote(authUser, roomId, connectionId, request);

        return ResponseEntity.status(HttpStatus.OK)
                .body(CommonResponse.success(NOTE_UPDATE_SUCCESS, response));
    }
}
