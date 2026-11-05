package com.shelved.domain.cases.dto;

import com.shelved.common.entity.json.Draft;
import com.shelved.common.entity.json.GlossaryTerm;
import com.shelved.common.enums.HotspotType;
import java.util.List;

/**
 * 개발용 시드 파일 1개 = 사건 1건. 관리자 제작 초안(Draft, docs/api/admin-draft-schema.md)과 같은 필드에 최상위 map 블록을 더한 형식.
 * 초안 필드는 Draft의 하위 레코드를 그대로 쓰므로 이름을 바꾸지 않는다.
 */
public record DevSeedFile(
        int schemaVersion,
        Draft.Card card,
        Draft.Source source,
        Draft.Briefing briefing,
        List<Draft.SuspectItem> suspects,
        List<Draft.EvidenceItem> evidences,
        Draft.Trick trick,
        String revealExplanation,
        List<GlossaryTerm> glossary,
        DevSeedMap map
) {

    /**
     * 맵 설정과 배치. 좌표는 맵 좌상단 원점의 월드 픽셀
     */
    public record DevSeedMap(
            String mapUrl,
            Integer spawnX,
            Integer spawnY,
            String collisionLayer,
            Integer interactionRadiusPx,
            List<DevSeedHotspot> hotspots
    ) {
    }

    /**
     * 배치 1건. key는 type에 따라 초안의 인물 키(sus-N) 또는 물증 키(evi-N)
     */
    public record DevSeedHotspot(
            HotspotType type,
            String key,
            Integer x,
            Integer y
    ) {
    }

    public List<Draft.SuspectItem> suspectList() {
        return suspects == null ? List.of() : suspects;
    }

    public List<Draft.EvidenceItem> evidenceList() {
        return evidences == null ? List.of() : evidences;
    }

    public List<Draft.TrickElementItem> trickElementList() {
        return trick == null || trick.elements() == null ? List.of() : trick.elements();
    }

    public List<DevSeedHotspot> hotspotList() {
        return map == null || map.hotspots() == null ? List.of() : map.hotspots();
    }
}
