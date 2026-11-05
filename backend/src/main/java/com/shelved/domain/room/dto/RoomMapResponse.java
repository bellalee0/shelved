package com.shelved.domain.room.dto;

import com.shelved.common.entity.CaseMap;
import com.shelved.common.entity.Evidence;
import com.shelved.common.entity.MapHotspot;
import com.shelved.common.entity.Suspect;
import java.util.List;

/**
 * 맵 설정과 배치 (docs/api/room.md). 물증 설명·범인 여부·NPC 내부 설정은 포함하지 않는다
 *
 * @param suspects  사건의 모든 인물 (지목 대상 목록 겸용). 미배치 인물은 x·y가 null
 * @param evidences 배치된 물증
 */
public record RoomMapResponse(
        String mapUrl,
        int spawnX,
        int spawnY,
        String collisionLayer,
        int interactionRadiusPx,
        List<RoomMapSuspect> suspects,
        List<RoomMapEvidence> evidences
) {

    public static RoomMapResponse from(CaseMap caseMap, List<RoomMapSuspect> suspects, List<RoomMapEvidence> evidences) {
        return new RoomMapResponse(
                caseMap.getMapUrl(),
                caseMap.getSpawnX(),
                caseMap.getSpawnY(),
                caseMap.getCollisionLayer(),
                caseMap.getInteractionRadiusPx(),
                suspects,
                evidences
        );
    }

    public record RoomMapSuspect(
            Long suspectId,
            String name,
            String spriteKey,
            boolean canConverse,
            Integer x,
            Integer y
    ) {

        /**
         * @param hotspot 배치 정보. 미배치면 null
         */
        public static RoomMapSuspect from(Suspect suspect, MapHotspot hotspot) {
            return new RoomMapSuspect(
                    suspect.getId(),
                    suspect.getName(),
                    suspect.getSpriteKey(),
                    suspect.isCanConverse(),
                    hotspot == null ? null : hotspot.getX(),
                    hotspot == null ? null : hotspot.getY()
            );
        }
    }

    public record RoomMapEvidence(
            Long evidenceId,
            String name,
            int x,
            int y
    ) {

        public static RoomMapEvidence from(Evidence evidence, MapHotspot hotspot) {
            return new RoomMapEvidence(
                    evidence.getId(),
                    evidence.getName(),
                    hotspot.getX(),
                    hotspot.getY()
            );
        }
    }
}
