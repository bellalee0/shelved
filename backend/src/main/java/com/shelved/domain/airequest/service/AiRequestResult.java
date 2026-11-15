package com.shelved.domain.airequest.service;

import com.shelved.common.dto.CommonResponse;
import com.shelved.common.enums.SuccessMessage;
import com.shelved.domain.airequest.dto.AiRequestStatusResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/**
 * AI 요청 처리 결과. 도메인 컨트롤러는 toResponseEntity로 200(결과)·202(처리 중)를 구분해 반환한다.
 * 실패(409·502 등)는 CustomException으로 던져지므로 여기에 없다
 */
public sealed interface AiRequestResult {

    /**
     * 200: 이번에 성공했거나 이미 성공한 요청. 응답은 항상 저장된 최초 성공 스냅샷과 같은 값이다
     */
    record Completed(JsonNode result) implements AiRequestResult {
    }

    /**
     * 202: 같은 requestId가 아직 처리 중
     */
    record Processing(AiRequestStatusResponse status) implements AiRequestResult {
    }

    /**
     * @param successMessage 200일 때의 성공 메시지 (예: NPC_ASK_SUCCESS)
     */
    default ResponseEntity<CommonResponse<Object>> toResponseEntity(SuccessMessage successMessage) {
        return switch (this) {
            case Completed completed -> ResponseEntity.status(HttpStatus.OK)
                    .body(CommonResponse.success(successMessage, completed.result()));
            case Processing processing -> ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(CommonResponse.success(SuccessMessage.AI_REQUEST_PROCESSING_SUCCESS, processing.status()));
        };
    }
}
