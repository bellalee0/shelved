package com.shelved.common.config;

import com.shelved.infra.llm.LlmEffort;
import com.shelved.infra.llm.LlmThinking;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 지목 채점 호출 설정값 (shelved.llm.grading). 모델은 본 응답 모델(shelved.llm.model, MAIN)을 쓴다.
 * 모든 값은 잠정이며 R3(INT-4) 실측 후 조정한다.
 *
 * <p>출력 토큰: 생각 토큰은 max_tokens 안에서 쓰인다. 채점 출력은 항목당 판정·근거·이유(1~2문장) + 피드백 몇 문장이라
 * 요소 5개 × 항목 4개(20항목)여도 대략 3~4천 토큰이다. 생각을 모델 기본값(Opus 5.5는 adaptive·effort medium)으로 두면
 * 생각에 수천 토큰을 더 쓸 수 있어 8192로 둔다. 실제 상한은 shelved.llm.max-output-tokens로 한 번 더 줄어든다.
 * 다만 비스트리밍 호출이라 출력이 길면 game-timeout(60초)에 먼저 걸릴 수 있다 — 지연은 R3에서 확인한다.</p>
 *
 * @param thinking          생각 설정. 없으면 MODEL_DEFAULT (본 응답과 같음). DISABLED는 Opus 5.5·Sonnet 5.5에서 400이다
 * @param effort            effort. 없으면 모델 기본값
 * @param maxOutputTokens   채점 호출 1회의 최대 출력 토큰 (생각 포함)
 * @param feedbackMaxLength 피드백 글자 수 상한 (UTF-16 코드 단위). 넘으면 형식 오류(채점 실패). 구조화 출력은 maxLength를 지원하지 않아 서버에서 검사한다
 */
@ConfigurationProperties(prefix = "shelved.llm.grading")
public record GradingProperties(
        LlmThinking thinking,
        LlmEffort effort,
        int maxOutputTokens,
        int feedbackMaxLength
) {

    public GradingProperties {
        if (thinking == null) {
            thinking = LlmThinking.MODEL_DEFAULT;
        }
        if (maxOutputTokens <= 0) {
            throw new IllegalArgumentException("shelved.llm.grading.max-output-tokens는 1 이상이어야 합니다.");
        }
        if (feedbackMaxLength <= 0) {
            throw new IllegalArgumentException("shelved.llm.grading.feedback-max-length는 1 이상이어야 합니다.");
        }
    }
}
