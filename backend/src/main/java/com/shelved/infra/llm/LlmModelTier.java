package com.shelved.infra.llm;

/**
 * 호출할 모델 종류. MAIN은 shelved.llm.model(본 응답), FILTER는 shelved.llm.filter-model(질문 분류용 작은 모델)
 */
public enum LlmModelTier {
    MAIN,
    FILTER
}
