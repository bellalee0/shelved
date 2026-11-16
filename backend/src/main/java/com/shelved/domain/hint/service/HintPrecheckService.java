package com.shelved.domain.hint.service;

import static com.shelved.common.exception.ErrorCode.HINT_FOLLOWUP_ALREADY_USED;
import static com.shelved.common.exception.ErrorCode.HINT_LIMIT_EXCEEDED;

import com.shelved.common.entity.Hint;
import com.shelved.common.entity.MysteryCase;
import com.shelved.common.entity.Room;
import com.shelved.common.exception.CustomException;
import com.shelved.common.jwt.AuthUser;
import com.shelved.domain.hint.repository.HintFollowupRepository;
import com.shelved.domain.hint.repository.HintRepository;
import com.shelved.domain.room.service.RoomPlayService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 질문 분류(비용이 드는 AI 호출) 전에 하는 값싼 사전 검사. 일시정지·종료된 세션, 남은 힌트가 없는 세션, 이미 쓴 보충 질문에
 * 분류 비용을 쓰지 않기 위함이다. 짧은 트랜잭션으로 끝나며 분류 호출 동안 잠금을 잡지 않는다.
 * 접수 트랜잭션(HintHandler·HintFollowupHandler.prepare)이 같은 검사를 다시 하며 그쪽이 최종 권위다
 */
@Service
@RequiredArgsConstructor
public class HintPrecheckService {

    private final RoomPlayService roomPlayService;
    private final HintRepository hintRepository;
    private final HintFollowupRepository hintFollowupRepository;
    private final HintUsageCounter hintUsageCounter;

    /**
     * 플레이 공통 검사(접근·연결 헤더·진행 중·남은 시간·현재 연결) → 남은 힌트(처리 중 확보분 포함, 409 HINT_LIMIT_EXCEEDED)
     *
     * @return 사건 ID (분류 호출 사용량 로그용)
     */
    @Transactional
    public Long validateHintRequestable(AuthUser authUser, Long roomId, String connectionId) {

        Room room = roomPlayService.acquirePlayableRoom(roomId, authUser, connectionId);
        MysteryCase mysteryCase = room.getMysteryCase();

        if (HintUsageCounter.isExhausted(hintUsageCounter.countHintUsage(room.getId(), mysteryCase.getDifficulty()))) {
            throw new CustomException(HINT_LIMIT_EXCEEDED);
        }

        return mysteryCase.getId();
    }

    /**
     * 플레이 공통 검사 → 이 세션의 힌트(404 HINT_NOT_FOUND) → 보충 질문 미사용(409 HINT_FOLLOWUP_ALREADY_USED). 힌트 횟수는 보지 않는다
     *
     * @return 사건 ID (분류 호출 사용량 로그용)
     */
    @Transactional
    public Long validateFollowupRequestable(AuthUser authUser, Long roomId, Long hintId, String connectionId) {

        Room room = roomPlayService.acquirePlayableRoom(roomId, authUser, connectionId);

        Hint hint = hintRepository.findHintOfRoom(hintId, room.getId());
        if (hintFollowupRepository.existsByHintId(hint.getId())) {
            throw new CustomException(HINT_FOLLOWUP_ALREADY_USED);
        }

        return room.getMysteryCase().getId();
    }
}
