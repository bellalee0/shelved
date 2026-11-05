package com.shelved.domain.room.dto;

import static com.shelved.common.constants.ValidationMessage.CASE_ID_NOT_NULL;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "세션 생성 요청")
public record RoomCreateRequest(
        @Schema(description = "공개된 사건 ID", example = "1")
        @NotNull(message = CASE_ID_NOT_NULL)
        Long caseId
) {
}
