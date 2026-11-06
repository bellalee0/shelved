package com.shelved.domain.evidence.controller;

import static com.shelved.common.enums.SuccessMessage.EVIDENCE_INSPECT_SUCCESS;

import com.shelved.common.dto.CommonResponse;
import com.shelved.common.jwt.AuthUser;
import com.shelved.domain.evidence.dto.EvidenceInspectResponse;
import com.shelved.domain.evidence.service.EvidenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "물증", description = "물증 조사")
@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class EvidenceController {

    private static final String CONNECTION_ID_HEADER = "X-Connection-Id";

    private final EvidenceService evidenceService;

    @Operation(summary = "물증 조사", description = "X-Connection-Id 필수. 세션 상태 공통 검사 후 이 사건의 물증이 아니면 404 EVIDENCE_NOT_FOUND. 반복 조사해도 같은 설명을 반환한다")
    @PostMapping("/{roomId}/evidences/{evidenceId}/inspect")
    public ResponseEntity<CommonResponse<EvidenceInspectResponse>> inspectEvidence(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long roomId,
            @PathVariable Long evidenceId,
            @RequestHeader(value = CONNECTION_ID_HEADER, required = false) String connectionId
    ) {
        EvidenceInspectResponse response = evidenceService.inspectEvidence(authUser, roomId, evidenceId, connectionId);

        return ResponseEntity.status(HttpStatus.OK)
                .body(CommonResponse.success(EVIDENCE_INSPECT_SUCCESS, response));
    }
}
