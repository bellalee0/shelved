package com.shelved.infra.llm;

import com.shelved.common.exception.CustomException;
import com.shelved.common.exception.ErrorCode;
import lombok.Getter;

/**
 * LLM 호출 실패. 메시지에는 실패 구분만 담고 프롬프트·응답 본문·API 키를 넣지 않는다
 */
@Getter
public class LlmException extends RuntimeException {

    private final LlmErrorType errorType;
    // LLM_INPUT_TOO_LARGE일 때만 값이 있다
    private final Integer estimatedInputTokens;
    private final Integer limitTokens;

    private LlmException(LlmErrorType errorType, String message, Integer estimatedInputTokens, Integer limitTokens) {
        super(errorType.name() + ": " + message);
        this.errorType = errorType;
        this.estimatedInputTokens = estimatedInputTokens;
        this.limitTokens = limitTokens;
    }

    public static LlmException timeout() {
        return new LlmException(LlmErrorType.LLM_TIMEOUT, "응답 시간 제한 초과", null, null);
    }

    /**
     * @param reason 상태 코드·예외 종류 등 민감하지 않은 원인
     */
    public static LlmException serviceError(String reason) {
        return new LlmException(LlmErrorType.LLM_SERVICE_ERROR, reason, null, null);
    }

    public static LlmException invalidResponse(String reason) {
        return new LlmException(LlmErrorType.LLM_INVALID_RESPONSE, reason, null, null);
    }

    public static LlmException inputTooLarge(int estimatedInputTokens, int limitTokens) {
        return new LlmException(LlmErrorType.LLM_INPUT_TOO_LARGE,
                "estimated=" + estimatedInputTokens + ", limit=" + limitTokens, estimatedInputTokens, limitTokens);
    }

    /**
     * 입력 한도 초과를 사용자 응답(400 LLM_INPUT_TOO_LARGE)으로 바꾼다. 관리자 제작 등 원문 입력 API에서 쓴다
     */
    public CustomException toInputTooLargeException() {
        if (errorType != LlmErrorType.LLM_INPUT_TOO_LARGE) {
            throw new IllegalStateException("입력 한도 초과 예외가 아닙니다: " + errorType);
        }
        return new CustomException(ErrorCode.LLM_INPUT_TOO_LARGE, new InputTooLargeDetail(estimatedInputTokens, limitTokens));
    }

    /**
     * 400 LLM_INPUT_TOO_LARGE의 detail (docs/api/errors.md)
     */
    public record InputTooLargeDetail(
            int estimatedInputTokens,
            int limitTokens
    ) {
    }
}
