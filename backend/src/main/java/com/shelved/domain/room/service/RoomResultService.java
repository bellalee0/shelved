package com.shelved.domain.room.service;

import static com.shelved.common.exception.ErrorCode.ROOM_NOT_ENDED;

import com.shelved.common.entity.MysteryCase;
import com.shelved.common.entity.PlayResult;
import com.shelved.common.entity.Room;
import com.shelved.common.enums.GradingStatus;
import com.shelved.common.enums.RoomStatus;
import com.shelved.common.exception.CustomException;
import com.shelved.common.jwt.AuthUser;
import com.shelved.domain.accusation.dto.AccusationResponse;
import com.shelved.domain.accusation.service.AccusationQueryService;
import com.shelved.domain.room.dto.RoomResultResponse;
import com.shelved.domain.room.dto.RoomStatusFilter;
import com.shelved.domain.room.repository.PlayResultRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 종료 세션의 결과(리빌) 조회
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoomResultService {

    private final PlayResultRepository playResultRepository;
    private final AccusationQueryService accusationQueryService;
    private final RoomAccessService roomAccessService;
    private final RoomTimeSettler roomTimeSettler;
    private final RoomSettlementService roomSettlementService;
    private final Clock clock;

    /**
     * 결과 조회. 최종 종료(CLEARED/FAILED/EXPIRED) 세션만 원작·정답을 반환한다.
     * 판정 전에 상태 조회(GET state)와 같은 정산 1·2를 적용한다(접속 확인 아님)
     */
    @Transactional
    public RoomResultResponse getRoomResult(AuthUser authUser, Long roomId) {

        Room room = roomAccessService.getAccessibleRoomForUpdate(roomId, authUser);
        boolean wasInProgress = room.getStatus() == RoomStatus.IN_PROGRESS;
        roomTimeSettler.settle(room, LocalDateTime.now(clock));
        if (!RoomStatusFilter.isEnded(room.getStatus())) {
            if (wasInProgress) {
                roomSettlementService.keepSettlementOnRollback(room.getId(), false);
            }
            throw new CustomException(ROOM_NOT_ENDED);
        }

        PlayResult playResult = playResultRepository.findByRoomId(roomId).orElse(null);
        validateResultData(room, playResult);

        List<AccusationResponse> accusationList = accusationQueryService.getAccusationResponseList(roomId);
        int completedCount = Math.toIntExact(accusationList.stream()
                .filter(accusation -> accusation.gradingStatus() == GradingStatus.COMPLETED)
                .count());

        return RoomResultResponse.of(room, playResult, completedCount, accusationList);
    }

    // 종료 세션인데 결과·리빌 데이터가 비어 있으면 데이터 오류(500). 로그에는 ID와 빠진 항목 이름만 남긴다
    private void validateResultData(Room room, PlayResult playResult) {
        MysteryCase mysteryCase = room.getMysteryCase();

        List<String> missingList = new ArrayList<>();
        if (playResult == null) {
            missingList.add("playResult");
        }
        if (!StringUtils.hasText(mysteryCase.getSourceNovelTitle())) {
            missingList.add("source.novelTitle");
        }
        if (!StringUtils.hasText(mysteryCase.getSourceNovelAuthor())) {
            missingList.add("source.novelAuthor");
        }
        if (!StringUtils.hasText(mysteryCase.getSourceNovelIntro())) {
            missingList.add("source.novelIntro");
        }
        if (mysteryCase.getCulpritSuspect() == null) {
            missingList.add("culprit");
        }
        if (!StringUtils.hasText(mysteryCase.getTrickSummary())) {
            missingList.add("trickSummary");
        }
        if (!StringUtils.hasText(mysteryCase.getRevealExplanation())) {
            missingList.add("revealExplanation");
        }
        if (missingList.isEmpty()) {
            return;
        }

        log.error("종료 세션 결과 데이터 누락 roomId={} caseId={} missing={}", room.getId(), mysteryCase.getId(), missingList);
        throw new IllegalStateException("종료 세션의 결과 데이터가 없습니다.");
    }
}
