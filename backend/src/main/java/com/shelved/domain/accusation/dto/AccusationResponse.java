package com.shelved.domain.accusation.dto;

import com.shelved.common.entity.Accusation;
import com.shelved.common.entity.AiRequest;
import com.shelved.common.enums.AiRequestStatus;
import com.shelved.common.enums.GradingStatus;
import com.shelved.common.enums.Verdict;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 지목 1건 (docs/api/hint-accusation.md AccusationResponse). 항목별 판정(element_results)·범인 일치(culprit_correct)·채점 기준은 넣지 않는다
 *
 * @param verdict          COMPLETED일 때만
 * @param feedback         COMPLETED일 때만
 * @param attemptNo        정상 채점 완료된 몇 번째 지목인지 (1부터). COMPLETED가 아니면 null
 * @param retryAvailableAt ERROR일 때 재채점 가능 시각 (요청 행의 마지막 갱신 + 채점 재시도 최소 간격). 그 외 null
 * @param superseded       채점 실패 후 새 답안으로 대체됨 (재채점 불가)
 */
public record AccusationResponse(
        Long accusationId,
        String requestId,
        Long suspectId,
        String suspectName,
        String trickExplanation,
        LocalDateTime submittedAt,
        GradingStatus gradingStatus,
        Verdict verdict,
        String feedback,
        Integer attemptNo,
        LocalDateTime retryAvailableAt,
        boolean superseded
) {

    /**
     * @param accusation              인물(suspect)을 읽을 수 있어야 한다
     * @param aiRequest               같은 requestId의 요청 행. 없으면 null (대체·재채점 시각을 알 수 없음)
     * @param attemptNo               COMPLETED일 때의 순번. 그 외에는 무시한다
     * @param gradingRetryMinInterval 채점 재시도 최소 간격
     */
    public static AccusationResponse of(
            Accusation accusation,
            AiRequest aiRequest,
            Integer attemptNo,
            Duration gradingRetryMinInterval
    ) {
        boolean completed = accusation.getGradingStatus() == GradingStatus.COMPLETED;
        boolean error = accusation.getGradingStatus() == GradingStatus.ERROR;
        LocalDateTime retryAvailableAt = error && aiRequest != null && aiRequest.getStatus() == AiRequestStatus.FAILED
                ? aiRequest.getUpdatedAt().plus(gradingRetryMinInterval)
                : null;

        return new AccusationResponse(
                accusation.getId(),
                accusation.getRequestId(),
                accusation.getSuspect().getId(),
                accusation.getSuspect().getName(),
                accusation.getTrickExplanation(),
                accusation.getSubmittedAt(),
                accusation.getGradingStatus(),
                completed ? accusation.getVerdict() : null,
                completed ? accusation.getFeedback() : null,
                completed ? attemptNo : null,
                retryAvailableAt,
                aiRequest != null && aiRequest.getSupersededAt() != null
        );
    }

    // 답안·피드백이 로그에 섞이지 않도록 record 기본 toString을 대체한다
    @Override
    public String toString() {
        return "AccusationResponse[accusationId=" + accusationId + ", requestId=" + requestId
                + ", gradingStatus=" + gradingStatus + ", verdict=" + verdict + "]";
    }
}
