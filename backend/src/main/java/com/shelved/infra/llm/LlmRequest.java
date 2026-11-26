package com.shelved.infra.llm;

import com.shelved.common.enums.LlmRequestType;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * LLM 호출 입력. 시스템 프롬프트·메시지·출력 스키마는 로그에 남기지 않는다
 *
 * @param requestType     사용량 로그의 요청 유형
 * @param modelTier       본 모델 / 분류 모델
 * @param systemPrompt    시스템 프롬프트 (없으면 null)
 * @param messageList     대화 메시지 (USER로 시작)
 * @param maxOutputTokens 최대 출력 토큰(생각 토큰 포함). shelved.llm.max-output-tokens를 넘으면 그 값으로 줄인다
 * @param outputSchema    구조화 출력의 JSON 스키마 (output_config.format.schema). null이면 자유 텍스트
 * @param thinking        생각 설정. 없으면 MODEL_DEFAULT
 * @param effort          effort (output_config.effort). null이면 모델 기본값
 */
public record LlmRequest(
        LlmRequestType requestType,
        LlmModelTier modelTier,
        String systemPrompt,
        List<LlmMessage> messageList,
        int maxOutputTokens,
        Map<String, Object> outputSchema,
        LlmThinking thinking,
        LlmEffort effort
) {

    public LlmRequest {
        Objects.requireNonNull(requestType, "requestType");
        Objects.requireNonNull(modelTier, "modelTier");
        messageList = List.copyOf(messageList);
        if (messageList.isEmpty()) {
            throw new IllegalArgumentException("메시지가 1개 이상 필요합니다.");
        }
        if (maxOutputTokens <= 0) {
            throw new IllegalArgumentException("maxOutputTokens는 양수여야 합니다.");
        }
        outputSchema = outputSchema == null ? null : Map.copyOf(outputSchema);
        if (thinking == null) {
            thinking = LlmThinking.MODEL_DEFAULT;
        }
    }

    /**
     * 자유 텍스트 응답, 생각·effort는 모델 기본값 (NPC 답변·힌트 등 기존 호출부)
     */
    public LlmRequest(
            LlmRequestType requestType,
            LlmModelTier modelTier,
            String systemPrompt,
            List<LlmMessage> messageList,
            int maxOutputTokens
    ) {
        this(requestType, modelTier, systemPrompt, messageList, maxOutputTokens, null, LlmThinking.MODEL_DEFAULT, null);
    }

    /**
     * 같은 요청에 구조화 출력 스키마를 지정한 사본
     */
    public LlmRequest withOutputSchema(Map<String, Object> schema) {
        return new LlmRequest(requestType, modelTier, systemPrompt, messageList, maxOutputTokens,
                Objects.requireNonNull(schema, "schema"), thinking, effort);
    }

    /**
     * 같은 요청에 생각·effort 설정을 지정한 사본 (effort가 null이면 모델 기본값)
     */
    public LlmRequest withThinking(LlmThinking thinking, LlmEffort effort) {
        return new LlmRequest(requestType, modelTier, systemPrompt, messageList, maxOutputTokens,
                outputSchema, thinking, effort);
    }

    // 프롬프트·메시지·스키마 내용이 로그에 섞이지 않도록 record 기본 toString을 대체한다
    @Override
    public String toString() {
        return "LlmRequest[requestType=" + requestType
                + ", modelTier=" + modelTier
                + ", messageCount=" + messageList.size()
                + ", maxOutputTokens=" + maxOutputTokens
                + ", structuredOutput=" + (outputSchema != null)
                + ", thinking=" + thinking
                + ", effort=" + effort + "]";
    }
}
