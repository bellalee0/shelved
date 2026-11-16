package com.shelved.domain.hint.service;

import com.shelved.common.entity.json.GlossaryTerm;
import java.util.List;

/**
 * 보충 질문 1건의 AI 호출 컨텍스트. 원래 힌트의 질문·답변과 용어집만 담는다.
 * 보충 질문은 원래 힌트를 풀어 설명할 뿐이므로 정답·트릭·NPC 설정·플레이 기록은 넣지 않는다 (새 정보의 근거 자체를 주지 않음)
 *
 * @param hintId           원래 힌트
 * @param hintQuestion     원래 힌트의 질문
 * @param hintContent      원래 힌트의 답변
 * @param followupQuestion 이번 보충 질문 원문
 */
public record HintFollowupContext(
        String requestId,
        Long hintId,
        List<GlossaryTerm> glossary,
        String hintQuestion,
        String hintContent,
        String followupQuestion
) {

    public HintFollowupContext {
        glossary = glossary == null ? List.of() : List.copyOf(glossary);
    }

    // 질문·답변이 로그에 섞이지 않도록 record 기본 toString을 대체한다
    @Override
    public String toString() {
        return "HintFollowupContext[requestId=" + requestId + ", hintId=" + hintId + "]";
    }
}
