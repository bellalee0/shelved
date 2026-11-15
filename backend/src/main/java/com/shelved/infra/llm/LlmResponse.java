package com.shelved.infra.llm;

/**
 * LLM 호출 결과. 사용량을 확인할 수 없으면 토큰 수는 0이 아닌 null이다
 *
 * @param text         응답 텍스트
 * @param inputTokens  입력 토큰 (미확인 시 null)
 * @param outputTokens 출력 토큰 (미확인 시 null)
 * @param model        실제 응답한 모델 식별자
 */
public record LlmResponse(
        String text,
        Integer inputTokens,
        Integer outputTokens,
        String model
) {

    public boolean usageKnown() {
        return inputTokens != null && outputTokens != null;
    }

    // 응답 전문이 로그에 섞이지 않도록 record 기본 toString을 대체한다
    @Override
    public String toString() {
        return "LlmResponse[length=" + (text == null ? 0 : text.length())
                + ", inputTokens=" + inputTokens
                + ", outputTokens=" + outputTokens
                + ", model=" + model + "]";
    }
}
