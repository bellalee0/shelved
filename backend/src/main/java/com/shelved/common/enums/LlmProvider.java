package com.shelved.common.enums;

/**
 * LLM 호출 제공자 (shelved.llm.provider). 기본값은 FAKE이며, 실제 Claude API 호출은 소유자가 CLAUDE로 지정할 때만 한다
 */
public enum LlmProvider {
    FAKE,
    CLAUDE
}
