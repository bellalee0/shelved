package com.shelved.fixture;

import com.shelved.common.entity.CaseMap;
import com.shelved.common.entity.CaseTrickElement;
import com.shelved.common.entity.Evidence;
import com.shelved.common.entity.MapHotspot;
import com.shelved.common.entity.MysteryCase;
import com.shelved.common.entity.Suspect;
import com.shelved.common.entity.json.Draft;
import com.shelved.common.entity.json.GlossaryTerm;
import com.shelved.common.entity.json.GradingRubric;
import com.shelved.common.entity.json.RubricItem;
import com.shelved.common.enums.CaseStatus;
import com.shelved.common.enums.ClueType;
import com.shelved.common.enums.Difficulty;
import com.shelved.common.enums.HotspotType;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 커밋용 가상 샘플 사건 「시계탑 저택의 마지막 종소리」(쉬움).
 * docs/api/·와이어프레임의 설명용 가상 예시만 사용하며 실제 소설·원작과 관련이 없다.
 * 프론트엔드 MSW 목도 같은 내용을 옮겨 쓴다 (2주차).
 */
public final class ClocktowerCaseFixture {

    public static final String DISPLAY_TITLE = "시계탑 저택의 마지막 종소리";
    public static final List<String> KEYWORD_TAGS = List.of("저택", "밀실", "알리바이");
    public static final String COVER_IMAGE_URL = "/uploads/covers/clocktower-sample.webp";

    public static final String SOURCE_NOVEL_TITLE = "The Last Chime";
    public static final String SOURCE_NOVEL_AUTHOR = "가상 작가";
    public static final String SOURCE_NOVEL_INTRO = "설명과 테스트를 위해 지어낸 가상의 원작입니다. 실제 소설과 관련이 없습니다.";

    public static final String INTRODUCTION = "시계탑이 있는 저택에서 주인이 서재에서 숨진 채 발견되었다.";
    public static final String INVESTIGATION_GOAL = "범인과 범행 방법을 밝혀라.";
    public static final String BACKGROUND_KNOWLEDGE = "사건 당시 저택에는 집사·가정부·정원사가 있었다.";

    public static final String TRICK_SUMMARY = "녹음기로 피해자가 살아 있는 시각을 조작했다";
    public static final String TRICK_ELEMENT_DESCRIPTION = "녹음기를 서재에 설치해 피해자가 살아 있는 시각을 조작했다";
    public static final String REVEAL_EXPLANATION =
            "해리스는 8시 50분에 서재에 녹음기를 설치해 9시 15분에 주인의 목소리가 들리게 만들었다. 실제로 주인은 그보다 먼저 숨졌다.";
    public static final String EASY_GUIDE_QUESTION = "사람들이 피해자의 목소리를 언제 들었다고 했나요?";

    public static final String MAP_URL = "/assets/maps/clocktower-mansion.json";
    public static final String COLLISION_LAYER = "collision";

    public static final List<GlossaryTerm> GLOSSARY = List.of(
            new GlossaryTerm("Harris", "해리스", "집사"),
            new GlossaryTerm("Margaret", "마거릿", "가정부"),
            new GlossaryTerm("Tom", "톰", "정원사"),
            new GlossaryTerm("Clocktower Hall", "시계탑 저택", "지명")
    );

    public static final List<RubricItem> RUBRIC_ITEMS = List.of(
            new RubricItem("trk-1-i1", "사용 도구", "녹음 장치를 사용했음을 밝힘",
                    List.of("녹음기", "소리를 재생하는 장치"), List.of()),
            new RubricItem("trk-1-i2", "사용 목적", "피해자가 살아 있는 시각을 늦춰 보이게 함",
                    List.of("사망 시각을 속였다"), List.of("비명을 가렸다"))
    );

    private ClocktowerCaseFixture() {
    }

    /**
     * 저장된 샘플 사건과 하위 엔티티
     */
    public record SampleCase(
            MysteryCase mysteryCase,
            Suspect butler,
            Suspect housekeeper,
            Suspect gardener,
            Suspect doctor,
            Evidence smallBox,
            Evidence tornMemo,
            CaseTrickElement trickElement,
            CaseMap caseMap,
            List<MapHotspot> hotspots
    ) {

        public List<Suspect> suspects() {
            return List.of(butler, housekeeper, gardener, doctor);
        }

        public List<Evidence> evidences() {
            return List.of(smallBox, tornMemo);
        }
    }

    /**
     * 출시(PUBLISHED)된 샘플 사건을 저장한다. 트랜잭션 안에서 호출한다.
     * 주치의 엘리스는 대화 불가·미배치 인물이다.
     */
    public static SampleCase persistPublished(EntityManager entityManager, LocalDateTime now) {

        MysteryCase mysteryCase = new MysteryCase(now);
        mysteryCase.updateCardInfo(DISPLAY_TITLE, Difficulty.EASY, KEYWORD_TAGS);
        mysteryCase.updateCoverImageUrl(COVER_IMAGE_URL);
        mysteryCase.updateSourceNovel(SOURCE_NOVEL_TITLE, SOURCE_NOVEL_AUTHOR, SOURCE_NOVEL_INTRO);
        mysteryCase.updateBriefing(INTRODUCTION, INVESTIGATION_GOAL, BACKGROUND_KNOWLEDGE);
        mysteryCase.updateGlossary(GLOSSARY);
        entityManager.persist(mysteryCase);

        Suspect butler = new Suspect(mysteryCase, "해리스",
                "[가상 사건] 저택의 집사 해리스. 정중하고 짧게 말한다. 9시에는 주방에서 은식기를 닦고 있었다고 진술한다. "
                        + "모순을 추궁받아도 자백하지 않고 답변을 거부한다.",
                "npc-butler-01", true, true);
        Suspect housekeeper = new Suspect(mysteryCase, "마거릿",
                "[가상 사건] 저택의 가정부 마거릿. 9시 15분쯤 서재에서 주인의 목소리를 들었다고 진술한다.",
                "npc-housekeeper-01", false, true);
        Suspect gardener = new Suspect(mysteryCase, "톰",
                "[가상 사건] 저택의 정원사 톰. 저녁 내내 정원에서 일했다고 진술한다.",
                "npc-gardener-01", false, true);
        Suspect doctor = new Suspect(mysteryCase, "엘리스", null, null, false, false);
        List.of(butler, housekeeper, gardener, doctor).forEach(entityManager::persist);

        Evidence smallBox = new Evidence(mysteryCase, "서재의 작은 상자",
                "책장 뒤에서 발견된 손바닥만 한 나무 상자. 안쪽에 태엽 장치가 들어 있다.");
        Evidence tornMemo = new Evidence(mysteryCase, "찢긴 메모",
                "서재 휴지통에서 나온 메모 조각. '9시 15분'이라는 글자만 알아볼 수 있다.");
        List.of(smallBox, tornMemo).forEach(entityManager::persist);

        GradingRubric rubric = new GradingRubric(
                RUBRIC_ITEMS,
                new GradingRubric.EasyFeedback(EASY_GUIDE_QUESTION, List.of(smallBox.getId()), List.of(butler.getId()))
        );
        CaseTrickElement trickElement = new CaseTrickElement(mysteryCase, TRICK_ELEMENT_DESCRIPTION, rubric, 1);
        entityManager.persist(trickElement);

        mysteryCase.updateSolution(butler, TRICK_SUMMARY, REVEAL_EXPLANATION);

        CaseMap caseMap = new CaseMap(mysteryCase, MAP_URL, 64, 64, COLLISION_LAYER, 48);
        entityManager.persist(caseMap);

        List<MapHotspot> hotspots = List.of(
                new MapHotspot(mysteryCase, HotspotType.SUSPECT, butler.getId(), 320, 192),
                new MapHotspot(mysteryCase, HotspotType.SUSPECT, housekeeper.getId(), 480, 256),
                new MapHotspot(mysteryCase, HotspotType.SUSPECT, gardener.getId(), 640, 448),
                new MapHotspot(mysteryCase, HotspotType.EVIDENCE, smallBox.getId(), 160, 128),
                new MapHotspot(mysteryCase, HotspotType.EVIDENCE, tornMemo.getId(), 224, 160)
        );
        hotspots.forEach(entityManager::persist);

        mysteryCase.changeStatus(CaseStatus.PUBLISHED);

        return new SampleCase(mysteryCase, butler, housekeeper, gardener, doctor,
                smallBox, tornMemo, trickElement, caseMap, hotspots);
    }

    /**
     * 같은 사건의 제작 초안 (확정 전, entityId 없음)
     */
    public static Draft draft() {

        Draft.SourceRef chapter2 = new Draft.SourceRef("2장 4문단", "...");

        Draft.Persona butlerPersona = new Draft.Persona(
                "정중하고 짧게 말한다",
                List.of(new Draft.Fact("서재에 녹음기가 있다는 것을 안다", List.of(chapter2))),
                List.of(),
                List.of(new Draft.Fact("9시에는 주방에서 은식기를 닦고 있었다", List.of())),
                List.of(new Draft.Fact("8시 50분에 서재에 들어갔다", List.of())),
                List.of(new Draft.Lie("9시의 행적", "주방에 있었다", "서재에서 녹음기를 설치했다", List.of())),
                List.of(),
                "모순을 추궁받아도 자백하지 않고 답변을 거부한다",
                List.of()
        );
        Draft.Persona housekeeperPersona = new Draft.Persona(
                "차분하고 자세히 말한다",
                List.of(),
                List.of(),
                List.of(new Draft.Fact("9시 15분쯤 서재에서 주인의 목소리를 들었다", List.of())),
                List.of(),
                List.of(),
                List.of(),
                "모르는 것은 모른다고 답한다",
                List.of()
        );
        Draft.Persona gardenerPersona = new Draft.Persona(
                "퉁명스럽게 말한다",
                List.of(),
                List.of(),
                List.of(new Draft.Fact("저녁 내내 정원에서 일했다", List.of())),
                List.of(),
                List.of(),
                List.of(),
                "같은 질문을 반복하면 짜증을 낸다",
                List.of()
        );

        List<Draft.SuspectItem> suspects = List.of(
                new Draft.SuspectItem("sus-1", null, "해리스", "저택의 집사. 20년째 근무", true, true,
                        "npc-butler-01", butlerPersona),
                new Draft.SuspectItem("sus-2", null, "마거릿", "저택의 가정부", true, false,
                        "npc-housekeeper-01", housekeeperPersona),
                new Draft.SuspectItem("sus-3", null, "톰", "저택의 정원사", true, false,
                        "npc-gardener-01", gardenerPersona),
                new Draft.SuspectItem("sus-4", null, "엘리스", "주치의. 사건 당일 저택에 없었다", false, false,
                        null, null)
        );

        List<Draft.EvidenceItem> evidences = List.of(
                new Draft.EvidenceItem("evi-1", null, "서재의 작은 상자",
                        "책장 뒤에서 발견된 손바닥만 한 나무 상자. 안쪽에 태엽 장치가 들어 있다.", List.of()),
                new Draft.EvidenceItem("evi-2", null, "찢긴 메모",
                        "서재 휴지통에서 나온 메모 조각. '9시 15분'이라는 글자만 알아볼 수 있다.", List.of())
        );

        Draft.TrickElementItem trickElement = new Draft.TrickElementItem(
                "trk-1",
                null,
                1,
                TRICK_ELEMENT_DESCRIPTION,
                new Draft.Rubric(RUBRIC_ITEMS,
                        new Draft.EasyFeedback(EASY_GUIDE_QUESTION, List.of("evi-1"), List.of("sus-1"))),
                List.of(
                        new Draft.SupportingClue(ClueType.EVIDENCE, "evi-1", "태엽 장치가 녹음기의 흔적"),
                        new Draft.SupportingClue(ClueType.TESTIMONY, "sus-2", "9시 15분에 목소리를 들음")
                )
        );

        return new Draft(
                Draft.CURRENT_SCHEMA_VERSION,
                new Draft.Card(DISPLAY_TITLE, Difficulty.EASY, KEYWORD_TAGS),
                new Draft.Source(SOURCE_NOVEL_TITLE, SOURCE_NOVEL_AUTHOR, SOURCE_NOVEL_INTRO),
                new Draft.Briefing(INTRODUCTION, INVESTIGATION_GOAL, BACKGROUND_KNOWLEDGE),
                suspects,
                evidences,
                new Draft.Trick(TRICK_SUMMARY, List.of(trickElement)),
                REVEAL_EXPLANATION,
                GLOSSARY
        );
    }
}
