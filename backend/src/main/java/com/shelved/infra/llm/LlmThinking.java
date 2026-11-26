package com.shelved.infra.llm;

/**
 * 요청의 생각(thinking) 설정. Messages API의 thinking 필드로 옮긴다 (ClaudeLlmClient)
 *
 * <ul>
 *     <li>MODEL_DEFAULT: thinking 필드를 보내지 않는다 (모델 기본값. Opus 5.5·Sonnet 5.5·Haiku 5.5는 adaptive로 켜짐)</li>
 *     <li>ADAPTIVE: {"type": "adaptive"}</li>
 *     <li>DISABLED: {"type": "disabled"}. Haiku 5.5는 effort high 이하에서만 허용, Opus 5.5·Sonnet 5.5는 400이다
 *     (claude-api 스킬 Thinking &amp; Effort 표, 2026-11 확인)</li>
 * </ul>
 */
public enum LlmThinking {
    MODEL_DEFAULT,
    ADAPTIVE,
    DISABLED
}
