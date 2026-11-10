package com.shelved.domain.cases.service;

import com.shelved.common.entity.json.Draft;
import com.shelved.common.enums.ClueType;
import com.shelved.common.enums.HotspotType;
import com.shelved.domain.cases.dto.DevSeedFile;
import com.shelved.domain.cases.dto.DevSeedFile.DevSeedHotspot;
import com.shelved.domain.cases.dto.DevSeedFile.DevSeedMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 개발용 시드의 최소 검사. 실패 사유에는 키(sus-N 등)와 필드 이름만 담고 시드 내용(이름·설명 등)은 담지 않는다 (로그에 남기므로).
 * 맵 파일 존재·spriteKey의 manifest 대조는 하지 않는다 (출시 검사 몫).
 */
final class DevSeedValidator {

    private static final int PUBLIC_INTRO_MAX_LENGTH = 200;

    private DevSeedValidator() {
    }

    /**
     * @return 실패 사유 목록. 비어 있으면 통과
     */
    static List<String> validate(DevSeedFile seed) {

        List<String> reasons = new ArrayList<>();

        if (seed.schemaVersion() != Draft.CURRENT_SCHEMA_VERSION) {
            reasons.add("schemaVersion은 " + Draft.CURRENT_SCHEMA_VERSION + "이어야 함");
        }
        validateCaseInfo(seed, reasons);

        Set<String> suspectKeys = validateSuspects(seed.suspectList(), reasons);
        Set<String> evidenceKeys = validateEvidences(seed.evidenceList(), reasons);
        validateTrick(seed, suspectKeys, evidenceKeys, reasons);
        validateMap(seed, suspectKeys, evidenceKeys, reasons);

        return reasons;
    }

    private static void validateCaseInfo(DevSeedFile seed, List<String> reasons) {
        if (seed.card() == null || isBlank(seed.card().displayTitle()) || seed.card().difficulty() == null) {
            reasons.add("card.displayTitle·difficulty 필수");
        }
        Draft.Briefing briefing = seed.briefing();
        if (briefing == null || isBlank(briefing.introduction()) || isBlank(briefing.investigationGoal())
                || isBlank(briefing.backgroundKnowledge())) {
            reasons.add("briefing의 세 항목 필수");
        }
    }

    private static Set<String> validateSuspects(List<Draft.SuspectItem> suspects, List<String> reasons) {
        Set<String> keys = new HashSet<>();
        if (suspects.isEmpty()) {
            reasons.add("suspects 비어 있음");
        }
        for (Draft.SuspectItem suspect : suspects) {
            if (isBlank(suspect.key()) || !keys.add(suspect.key())) {
                reasons.add("인물 키 누락 또는 중복");
            }
            if (isBlank(suspect.name())) {
                reasons.add("인물 이름 누락: " + suspect.key());
            }
            if (suspect.publicIntro() != null
                    && (suspect.publicIntro().isBlank() || suspect.publicIntro().length() > PUBLIC_INTRO_MAX_LENGTH)) {
                reasons.add("publicIntro는 1~" + PUBLIC_INTRO_MAX_LENGTH + "자: " + suspect.key());
            }
        }
        long culpritCount = suspects.stream().filter(Draft.SuspectItem::isCulprit).count();
        if (culpritCount != 1) {
            reasons.add("범인(isCulprit=true)은 정확히 1명이어야 함 (현재 " + culpritCount + "명)");
        }
        return keys;
    }

    private static Set<String> validateEvidences(List<Draft.EvidenceItem> evidences, List<String> reasons) {
        Set<String> keys = new HashSet<>();
        for (Draft.EvidenceItem evidence : evidences) {
            if (isBlank(evidence.key()) || !keys.add(evidence.key())) {
                reasons.add("물증 키 누락 또는 중복");
            }
            if (isBlank(evidence.name()) || isBlank(evidence.description())) {
                reasons.add("물증 이름·설명 누락: " + evidence.key());
            }
        }
        return keys;
    }

    private static void validateTrick(
            DevSeedFile seed,
            Set<String> suspectKeys,
            Set<String> evidenceKeys,
            List<String> reasons
    ) {
        if (seed.trick() == null) {
            reasons.add("trick 필수");
            return;
        }
        Set<String> keys = new HashSet<>();
        Set<Integer> orderIndexes = new HashSet<>();
        for (Draft.TrickElementItem element : seed.trickElementList()) {
            String key = element.key();
            if (isBlank(key) || !keys.add(key)) {
                reasons.add("트릭 요소 키 누락 또는 중복");
            }
            if (element.orderIndex() == null || element.orderIndex() < 1 || !orderIndexes.add(element.orderIndex())) {
                reasons.add("orderIndex는 1 이상이며 중복 불가: " + key);
            }
            if (isBlank(element.description())) {
                reasons.add("트릭 요소 설명 누락: " + key);
            }
            Draft.Rubric rubric = element.rubric();
            if (rubric == null || rubric.items() == null || rubric.items().isEmpty()) {
                reasons.add("rubric.items 필수: " + key);
            }
            if (rubric != null && rubric.easyFeedback() != null) {
                Draft.EasyFeedback feedback = rubric.easyFeedback();
                validateKeyRefs(feedback.evidenceKeys(), evidenceKeys, "easyFeedback.evidenceKeys", key, reasons);
                validateKeyRefs(feedback.suspectKeys(), suspectKeys, "easyFeedback.suspectKeys", key, reasons);
            }
            if (element.supportingClues() != null) {
                for (Draft.SupportingClue clue : element.supportingClues()) {
                    Set<String> targetKeys = clue.type() == ClueType.EVIDENCE ? evidenceKeys : suspectKeys;
                    if (clue.type() == null || !targetKeys.contains(clue.refKey())) {
                        reasons.add("supportingClues가 없는 키를 참조: " + key + " -> " + clue.refKey());
                    }
                }
            }
        }
    }

    private static void validateMap(
            DevSeedFile seed,
            Set<String> suspectKeys,
            Set<String> evidenceKeys,
            List<String> reasons
    ) {
        DevSeedMap map = seed.map();
        if (map == null) {
            reasons.add("map 필수");
            return;
        }
        if (isBlank(map.mapUrl()) || isBlank(map.collisionLayer())) {
            reasons.add("map.mapUrl·collisionLayer 필수");
        }
        if (isNegativeOrNull(map.spawnX()) || isNegativeOrNull(map.spawnY())) {
            reasons.add("map.spawnX·spawnY는 0 이상");
        }
        if (map.interactionRadiusPx() == null || map.interactionRadiusPx() <= 0) {
            reasons.add("map.interactionRadiusPx는 1 이상");
        }

        Set<String> placedSuspectKeys = new HashSet<>();
        Set<String> placedEvidenceKeys = new HashSet<>();
        for (DevSeedHotspot hotspot : seed.hotspotList()) {
            String key = hotspot.key();
            if (hotspot.type() == null) {
                reasons.add("hotspot type 누락: " + key);
                continue;
            }
            boolean suspectType = hotspot.type() == HotspotType.SUSPECT;
            Set<String> targetKeys = suspectType ? suspectKeys : evidenceKeys;
            Set<String> placedKeys = suspectType ? placedSuspectKeys : placedEvidenceKeys;
            if (!targetKeys.contains(key)) {
                reasons.add("hotspot이 없는 키를 참조: " + hotspot.type() + " " + key);
            } else if (!placedKeys.add(key)) {
                reasons.add("같은 대상의 hotspot 중복: " + key);
            }
            if (isNegativeOrNull(hotspot.x()) || isNegativeOrNull(hotspot.y())) {
                reasons.add("hotspot 좌표는 0 이상: " + key);
            }
        }

        for (Draft.SuspectItem suspect : seed.suspectList()) {
            boolean placed = placedSuspectKeys.contains(suspect.key());
            if (suspect.canConverse() && !placed) {
                reasons.add("대화 가능 인물 미배치: " + suspect.key());
            }
            if (placed && isBlank(suspect.spriteKey())) {
                reasons.add("배치된 인물의 spriteKey 누락: " + suspect.key());
            }
        }
        for (Draft.EvidenceItem evidence : seed.evidenceList()) {
            if (!placedEvidenceKeys.contains(evidence.key())) {
                reasons.add("물증 미배치: " + evidence.key());
            }
        }
    }

    private static void validateKeyRefs(
            List<String> refs,
            Set<String> existingKeys,
            String field,
            String elementKey,
            List<String> reasons
    ) {
        if (refs == null) {
            return;
        }
        for (String ref : refs) {
            if (!existingKeys.contains(ref)) {
                reasons.add(field + "가 없는 키를 참조: " + elementKey + " -> " + ref);
            }
        }
    }

    private static boolean isNegativeOrNull(Integer value) {
        return value == null || value < 0;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
