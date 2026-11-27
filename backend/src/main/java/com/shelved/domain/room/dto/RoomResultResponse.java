package com.shelved.domain.room.dto;

import com.shelved.common.entity.MysteryCase;
import com.shelved.common.entity.PlayResult;
import com.shelved.common.entity.Room;
import com.shelved.common.enums.RoomStatus;
import com.shelved.domain.accusation.dto.AccusationResponse;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 종료 세션의 리빌·플레이 기록 (docs/api/room.md GET result).
 * 원작·정답을 담으므로 최종 종료를 확인한 뒤에만 만든다. 채점 기준·항목별 판정·인물 내부 설정·용어집은 넣지 않는다
 */
public record RoomResultResponse(
        Long roomId,
        Long caseId,
        String displayTitle,
        boolean isTest,
        RoomStatus status,
        LocalDateTime endedAt,
        int durationSec,
        int hintUsedCount,
        int accusationCompletedCount,
        RoomResultSource source,
        RoomResultCulprit culprit,
        String trickSummary,
        String revealExplanation,
        List<AccusationResponse> accusations
) {

    /**
     * @param room       최종 종료된 세션. 사건과 범인을 읽을 수 있어야 한다
     * @param playResult 이 세션의 종료 결과
     */
    public static RoomResultResponse of(
            Room room,
            PlayResult playResult,
            int accusationCompletedCount,
            List<AccusationResponse> accusations
    ) {
        MysteryCase mysteryCase = room.getMysteryCase();

        return new RoomResultResponse(
                room.getId(),
                mysteryCase.getId(),
                mysteryCase.getDisplayTitle(),
                room.isTest(),
                room.getStatus(),
                playResult.getEndedAt(),
                playResult.getDurationSec(),
                playResult.getHintUsedCount(),
                accusationCompletedCount,
                RoomResultSource.from(mysteryCase),
                RoomResultCulprit.from(mysteryCase.getCulpritSuspect()),
                mysteryCase.getTrickSummary(),
                mysteryCase.getRevealExplanation(),
                accusations
        );
    }

    // 원작·정답·답안이 로그에 섞이지 않도록 record 기본 toString을 대체한다
    @Override
    public String toString() {
        return "RoomResultResponse[roomId=" + roomId + ", caseId=" + caseId + ", status=" + status + "]";
    }
}
