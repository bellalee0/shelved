package com.shelved.domain.evidence.service;

import com.shelved.common.entity.Evidence;
import com.shelved.common.entity.EvidenceInspection;
import com.shelved.common.entity.Room;
import com.shelved.common.jwt.AuthUser;
import com.shelved.domain.cases.repository.EvidenceRepository;
import com.shelved.domain.evidence.dto.EvidenceInspectResponse;
import com.shelved.domain.evidence.repository.EvidenceInspectionRepository;
import com.shelved.domain.room.service.RoomPlayService;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EvidenceService {

    private final RoomPlayService roomPlayService;
    private final EvidenceRepository evidenceRepository;
    private final EvidenceInspectionRepository evidenceInspectionRepository;
    private final Clock clock;

    /**
     * 물증 조사. 검수된 고정 설명을 반환하고(AI 미사용), 최초 조사 이력만 세션·물증당 1건 저장한다
     */
    @Transactional
    public EvidenceInspectResponse inspectEvidence(AuthUser authUser, Long roomId, Long evidenceId, String connectionId) {

        Room room = roomPlayService.acquirePlayableRoom(roomId, authUser, connectionId);
        Evidence evidence = evidenceRepository.findEvidenceOfCase(evidenceId, room.getMysteryCase().getId());

        // 세션 행 잠금으로 직렬화되므로 확인 후 저장으로 충분하다. UNIQUE(room_id, evidence_id)가 마지막 방어선
        if (!evidenceInspectionRepository.existsByRoomIdAndEvidenceId(roomId, evidenceId)) {
            evidenceInspectionRepository.save(new EvidenceInspection(room, evidence, LocalDateTime.now(clock)));
        }

        return EvidenceInspectResponse.from(evidence);
    }
}
