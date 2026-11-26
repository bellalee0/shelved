package com.shelved.infra.llm;

/**
 * 요청의 effort. Messages API의 output_config.effort(low/medium/high/xhigh/max)로 옮긴다.
 * 요청에 없으면(null) 보내지 않아 모델 기본값을 쓴다 (Opus 5.5·Haiku 5.5는 medium, 그 밖의 현재 모델은 high)
 */
public enum LlmEffort {
    LOW,
    MEDIUM,
    HIGH,
    XHIGH,
    MAX;

    public String apiValue() {
        return name().toLowerCase();
    }
}
