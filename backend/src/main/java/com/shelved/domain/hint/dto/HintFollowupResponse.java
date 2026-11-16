package com.shelved.domain.hint.dto;

import com.shelved.common.entity.HintFollowup;
import java.time.LocalDateTime;

/**
 * 보충 질문·답변 (docs/api/hint-accusation.md HintFollowupResponse). 보충 질문 POST의 성공 응답이기도 하다
 */
public record HintFollowupResponse(
        Long hintId,
        String requestId,
        String question,
        String content,
        LocalDateTime createdAt
) {

    public static HintFollowupResponse from(HintFollowup followup) {
        return new HintFollowupResponse(
                followup.getHint().getId(),
                followup.getRequestId(),
                followup.getQuestion(),
                followup.getContent(),
                followup.getCreatedAt()
        );
    }
}
