package com.shelved.domain.airequest.dto;

import com.shelved.common.entity.AiRequest;
import com.shelved.common.entity.json.AiRequestInput;
import com.shelved.common.enums.AiRequestStatus;
import com.shelved.common.enums.AiRequestType;
import com.shelved.common.exception.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Duration;
import java.time.LocalDateTime;
import tools.jackson.databind.JsonNode;

/**
 * AI 요청의 처리 상태 (docs/api/room.md AiRequestStatusResponse). AI POST의 202, 상태 조회 API, 세션 상태의 aiRequests에서 쓴다.
 *
 * <p>input은 AI_REQUESTS.input_json을 그대로 매핑한다. input_json에는 플레이어가 직접 보낸 값(question 등)만 저장하며,
 * 내부 프롬프트·인물 설정·대화 이력 등 AI에 전달한 내부 컨텍스트는 앞으로도 같은 컬럼에 넣지 않는다.
 * 내부 컨텍스트를 저장해야 하면 별도 컬럼·테이블에 두고 이 응답에 포함하지 않는다 (backend.md 정답 정보 보호 규칙).</p>
 *
 * @param targetId         NPC_ASK: suspectId, HINT_FOLLOWUP: hintId, 그 외 null
 * @param result           SUCCEEDED일 때 최초 성공 당시 원래 POST 성공 응답의 스냅샷 (현재 값으로 재계산하지 않음)
 * @param error            FAILED일 때 { code, message }
 * @param retryAvailableAt ACCUSATION이 FAILED일 때 같은 답안을 다시 채점할 수 있는 시각 (실패 시각 + 채점 재시도 최소 간격)
 * @param acceptedAt       서버 최초 접수 시각
 */
public record AiRequestStatusResponse(
        String requestId,
        AiRequestType type,
        Long targetId,
        AiRequestStatus status,
        AiRequestInput input,
        @Schema(description = "최초 성공 응답 스냅샷. 유형별 POST 성공 응답과 같은 객체")
        JsonNode result,
        AiRequestError error,
        LocalDateTime retryAvailableAt,
        LocalDateTime acceptedAt,
        LocalDateTime updatedAt
) {

    public static AiRequestStatusResponse from(AiRequest aiRequest, Duration gradingRetryMinInterval) {
        boolean failed = aiRequest.getStatus() == AiRequestStatus.FAILED;
        boolean succeeded = aiRequest.getStatus() == AiRequestStatus.SUCCEEDED;
        LocalDateTime retryAvailableAt = failed && aiRequest.getRequestType() == AiRequestType.ACCUSATION
                ? aiRequest.getUpdatedAt().plus(gradingRetryMinInterval)
                : null;

        return new AiRequestStatusResponse(
                aiRequest.getRequestId(),
                aiRequest.getRequestType(),
                aiRequest.getTargetId(),
                aiRequest.getStatus(),
                aiRequest.getInputJson(),
                succeeded ? aiRequest.getResultJson() : null,
                failed ? AiRequestError.from(aiRequest) : null,
                retryAvailableAt,
                aiRequest.getAcceptedAt(),
                aiRequest.getUpdatedAt()
        );
    }

    /**
     * 실패 정보. message는 ErrorCode의 사용자용 메시지
     */
    public record AiRequestError(
            String code,
            String message
    ) {

        static AiRequestError from(AiRequest aiRequest) {
            ErrorCode errorCode = errorCodeOf(aiRequest);
            return new AiRequestError(errorCode.name(), errorCode.getMessage());
        }

        // 저장된 코드가 없거나 더 이상 없는 이름이면 유형별 실패 코드로 보여 준다
        private static ErrorCode errorCodeOf(AiRequest aiRequest) {
            String stored = aiRequest.getErrorCode();
            if (stored != null) {
                for (ErrorCode errorCode : ErrorCode.values()) {
                    if (errorCode.name().equals(stored)) {
                        return errorCode;
                    }
                }
            }
            return aiRequest.getRequestType().getFailureCode();
        }
    }
}
