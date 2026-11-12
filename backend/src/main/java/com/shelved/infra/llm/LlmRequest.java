package com.shelved.infra.llm;

import com.shelved.common.enums.LlmRequestType;
import java.util.List;
import java.util.Objects;

/**
 * LLM 호출 입력. 시스템 프롬프트와 메시지는 로그에 남기지 않는다
 *
 * @param requestType     사용량 로그의 요청 유형
 * @param modelTier       본 모델 / 분류 모델
 * @param systemPrompt    시스템 프롬프트 (없으면 null)
 * @param messageList     대화 메시지 (USER로 시작)
 * @param maxOutputTokens 최대 출력 토큰. shelved.llm.max-output-tokens를 넘으면 그 값으로 줄인다
 */
public record LlmRequest(
        LlmRequestType requestType,
        LlmModelTier modelTier,
        String systemPrompt,
        List<LlmMessage> messageList,
        int maxOutputTokens
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
    }

    // 프롬프트·메시지 내용이 로그에 섞이지 않도록 record 기본 toString을 대체한다
    @Override
    public String toString() {
        return "LlmRequest[requestType=" + requestType
                + ", modelTier=" + modelTier
                + ", messageCount=" + messageList.size()
                + ", maxOutputTokens=" + maxOutputTokens + "]";
    }
}
