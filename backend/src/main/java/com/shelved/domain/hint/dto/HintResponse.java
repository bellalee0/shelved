package com.shelved.domain.hint.dto;

import com.shelved.common.entity.Hint;
import java.time.LocalDateTime;

/**
 * 힌트 1건 (docs/api/hint-accusation.md HintResponse)
 *
 * @param followup          보충 질문·답변. 없으면 null
 * @param followupAvailable 미사용 보충 질문 기회가 남았는지 (followup == null이고 처리 중인 보충 질문이 없음). 세션 상태와 무관
 */
public record HintResponse(
        Long hintId,
        String requestId,
        String question,
        String content,
        LocalDateTime createdAt,
        HintFollowupResponse followup,
        boolean followupAvailable
) {

    public HintResponse(Hint hint, HintFollowupResponse followup, boolean followupAvailable) {
        this(hint.getId(), hint.getRequestId(), hint.getQuestion(), hint.getContent(), hint.getCreatedAt(),
                followup, followupAvailable);
    }
}
