package com.shelved.domain.npc.service;

import com.shelved.common.entity.Room;
import com.shelved.common.entity.Suspect;
import com.shelved.common.jwt.AuthUser;
import com.shelved.domain.cases.repository.SuspectRepository;
import com.shelved.domain.room.service.RoomPlayService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 질문 분류(비용이 드는 AI 호출) 전에 하는 값싼 사전 검사. 일시정지·종료된 세션이나 질문할 수 없는 인물에 분류 비용을 쓰지 않기 위함이다.
 * 짧은 트랜잭션으로 끝나며 분류 호출 동안 잠금을 잡지 않는다. 접수 트랜잭션이 같은 검사를 다시 하며 그쪽이 최종 권위다
 */
@Service
@RequiredArgsConstructor
public class NpcAskPrecheckService {

    private final RoomPlayService roomPlayService;
    private final SuspectRepository suspectRepository;
    private final NpcSuspectValidator npcSuspectValidator;

    /**
     * 플레이 공통 검사(접근·연결 헤더·진행 중·남은 시간·현재 연결) → 이 사건의 인물(404 SUSPECT_NOT_FOUND) → 대화 가능(400 NPC_NOT_CONVERSABLE)
     *
     * @return 사건 ID (분류 호출 사용량 로그용)
     */
    @Transactional
    public Long validateAskable(AuthUser authUser, Long roomId, Long suspectId, String connectionId) {

        Room room = roomPlayService.acquirePlayableRoom(roomId, authUser, connectionId);
        Long caseId = room.getMysteryCase().getId();

        Suspect suspect = suspectRepository.findSuspectOfCase(suspectId, caseId);
        npcSuspectValidator.validateConversable(suspect);

        return caseId;
    }
}
