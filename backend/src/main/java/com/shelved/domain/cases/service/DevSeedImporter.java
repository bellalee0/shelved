package com.shelved.domain.cases.service;

import com.shelved.common.entity.CaseDraft;
import com.shelved.common.entity.CaseMap;
import com.shelved.common.entity.CaseTrickElement;
import com.shelved.common.entity.Evidence;
import com.shelved.common.entity.MapHotspot;
import com.shelved.common.entity.MysteryCase;
import com.shelved.common.entity.Suspect;
import com.shelved.common.entity.json.Draft;
import com.shelved.common.entity.json.GradingRubric;
import com.shelved.common.enums.CaseStatus;
import com.shelved.common.enums.HotspotType;
import com.shelved.domain.cases.dto.DevSeedFile;
import com.shelved.domain.cases.dto.DevSeedFile.DevSeedHotspot;
import com.shelved.domain.cases.dto.DevSeedFile.DevSeedMap;
import com.shelved.domain.cases.repository.CaseDraftRepository;
import com.shelved.domain.cases.repository.CaseMapRepository;
import com.shelved.domain.cases.repository.CaseTrickElementRepository;
import com.shelved.domain.cases.repository.EvidenceRepository;
import com.shelved.domain.cases.repository.MapHotspotRepository;
import com.shelved.domain.cases.repository.MysteryCaseRepository;
import com.shelved.domain.cases.repository.SuspectRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 검사(DevSeedValidator)를 통과한 시드 1건을 PUBLISHED 사건으로 반영한다 (한 파일 = 한 트랜잭션).
 * persona_prompt는 비워 두고(3주차 NPC 작업에서 변환), 원본을 다시 읽을 수 있도록 entityId를 채운 초안을 CASE_DRAFTS에 함께 저장한다.
 */
@Service
@RequiredArgsConstructor
public class DevSeedImporter {

    private final MysteryCaseRepository mysteryCaseRepository;
    private final SuspectRepository suspectRepository;
    private final EvidenceRepository evidenceRepository;
    private final CaseTrickElementRepository caseTrickElementRepository;
    private final CaseMapRepository caseMapRepository;
    private final MapHotspotRepository mapHotspotRepository;
    private final CaseDraftRepository caseDraftRepository;
    private final Clock clock;

    /**
     * @return 반영한 사건 ID. 같은 표시명의 PUBLISHED 사건이 이미 있으면 반영하지 않고 empty
     */
    @Transactional
    public Optional<Long> importSeed(DevSeedFile seed) {

        if (mysteryCaseRepository.existsByDisplayTitleAndStatus(seed.card().displayTitle(), CaseStatus.PUBLISHED)) {
            return Optional.empty();
        }
        LocalDateTime now = LocalDateTime.now(clock);

        MysteryCase mysteryCase = new MysteryCase(now);
        Draft.Card card = seed.card();
        mysteryCase.updateCardInfo(card.displayTitle(), card.difficulty(), card.keywordTags());
        if (seed.source() != null) {
            mysteryCase.updateSourceNovel(seed.source().novelTitle(), seed.source().novelAuthor(), seed.source().novelIntro());
        }
        Draft.Briefing briefing = seed.briefing();
        mysteryCase.updateBriefing(briefing.introduction(), briefing.investigationGoal(), briefing.backgroundKnowledge());
        mysteryCase.updateGlossary(seed.glossary());
        mysteryCaseRepository.save(mysteryCase);

        Map<String, Suspect> suspectByKey = new LinkedHashMap<>();
        for (Draft.SuspectItem item : seed.suspectList()) {
            Suspect suspect = new Suspect(mysteryCase, item.name(), null, item.spriteKey(), item.publicIntro(), item.isCulprit(), item.canConverse());
            suspectByKey.put(item.key(), suspectRepository.save(suspect));
        }
        Map<String, Evidence> evidenceByKey = new LinkedHashMap<>();
        for (Draft.EvidenceItem item : seed.evidenceList()) {
            Evidence evidence = new Evidence(mysteryCase, item.name(), item.description());
            evidenceByKey.put(item.key(), evidenceRepository.save(evidence));
        }
        Map<String, CaseTrickElement> trickElementByKey = new LinkedHashMap<>();
        for (Draft.TrickElementItem item : seed.trickElementList()) {
            GradingRubric rubric = toGradingRubric(item.rubric(), suspectByKey, evidenceByKey);
            CaseTrickElement element = new CaseTrickElement(mysteryCase, item.description(), rubric, item.orderIndex());
            trickElementByKey.put(item.key(), caseTrickElementRepository.save(element));
        }

        Suspect culprit = seed.suspectList().stream()
                .filter(Draft.SuspectItem::isCulprit)
                .map(item -> suspectByKey.get(item.key()))
                .findFirst()
                .orElseThrow();
        mysteryCase.updateSolution(culprit, seed.trick().summary(), seed.revealExplanation());

        DevSeedMap map = seed.map();
        caseMapRepository.save(new CaseMap(mysteryCase, map.mapUrl(), map.spawnX(), map.spawnY(),
                map.collisionLayer(), map.interactionRadiusPx()));
        for (DevSeedHotspot hotspot : seed.hotspotList()) {
            Long refId = hotspot.type() == HotspotType.SUSPECT
                    ? suspectByKey.get(hotspot.key()).getId()
                    : evidenceByKey.get(hotspot.key()).getId();
            mapHotspotRepository.save(new MapHotspot(mysteryCase, hotspot.type(), refId, hotspot.x(), hotspot.y()));
        }

        caseDraftRepository.save(new CaseDraft(mysteryCase,
                toDraftWithEntityIds(seed, suspectByKey, evidenceByKey, trickElementByKey), now));

        mysteryCase.markContentUpdated(now);
        mysteryCase.markTestVerified(now);
        mysteryCase.changeStatus(CaseStatus.PUBLISHED);

        return Optional.of(mysteryCase.getId());
    }

    // 초안의 물증·인물 키를 엔티티 ID로 바꾼 판정 기준
    private static GradingRubric toGradingRubric(
            Draft.Rubric rubric,
            Map<String, Suspect> suspectByKey,
            Map<String, Evidence> evidenceByKey
    ) {
        Draft.EasyFeedback feedback = rubric.easyFeedback();
        if (feedback == null) {
            return new GradingRubric(rubric.items(), null);
        }
        List<Long> evidenceIds = orEmpty(feedback.evidenceKeys()).stream()
                .map(key -> evidenceByKey.get(key).getId())
                .toList();
        List<Long> suspectIds = orEmpty(feedback.suspectKeys()).stream()
                .map(key -> suspectByKey.get(key).getId())
                .toList();

        return new GradingRubric(rubric.items(),
                new GradingRubric.EasyFeedback(feedback.guideQuestion(), evidenceIds, suspectIds));
    }

    private static Draft toDraftWithEntityIds(
            DevSeedFile seed,
            Map<String, Suspect> suspectByKey,
            Map<String, Evidence> evidenceByKey,
            Map<String, CaseTrickElement> trickElementByKey
    ) {
        List<Draft.SuspectItem> suspects = seed.suspectList().stream()
                .map(item -> new Draft.SuspectItem(item.key(), suspectByKey.get(item.key()).getId(), item.name(),
                        item.role(), item.publicIntro(), item.canConverse(), item.isCulprit(), item.spriteKey(), item.persona()))
                .toList();
        List<Draft.EvidenceItem> evidences = seed.evidenceList().stream()
                .map(item -> new Draft.EvidenceItem(item.key(), evidenceByKey.get(item.key()).getId(), item.name(),
                        item.description(), item.sourceRefs()))
                .toList();
        List<Draft.TrickElementItem> elements = seed.trickElementList().stream()
                .map(item -> new Draft.TrickElementItem(item.key(), trickElementByKey.get(item.key()).getId(),
                        item.orderIndex(), item.description(), item.rubric(), item.supportingClues()))
                .toList();

        return new Draft(
                seed.schemaVersion(),
                seed.card(),
                seed.source(),
                seed.briefing(),
                suspects,
                evidences,
                new Draft.Trick(seed.trick().summary(), elements),
                seed.revealExplanation(),
                seed.glossary()
        );
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }
}
