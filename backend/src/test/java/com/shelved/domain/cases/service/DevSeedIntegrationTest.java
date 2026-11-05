package com.shelved.domain.cases.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.shelved.IntegrationTestSupport;
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
import com.shelved.domain.cases.service.DevSeedService.DevSeedLoadResult;
import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 커밋된 가상 예시 시드(src/test/resources/dev-seed/clocktower-sample.json)로 로더를 확인한다.
 * 다른 테스트가 같은 표시명의 공개 사건을 저장하므로, 표시명만 고유한 값으로 바꾼 사본을 임시 폴더에 두고 읽는다.
 */
class DevSeedIntegrationTest extends IntegrationTestSupport {

    private static final String SAMPLE_SEED = "dev-seed/clocktower-sample.json";
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @Autowired
    private DevSeedService devSeedService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @TempDir
    private Path seedDir;

    @Test
    void loadAll_가상예시시드_사건과하위엔티티반영() throws IOException {

        // given
        String title = writeSeed("clocktower.json", seed -> { });

        // when
        DevSeedLoadResult result = devSeedService.loadAll(seedPath());

        // then
        assertThat(result).isEqualTo(new DevSeedLoadResult(1, 0, 0));
        transactionTemplate.executeWithoutResult(status -> {
            MysteryCase mysteryCase = findPublishedCases(title).getFirst();
            Long caseId = mysteryCase.getId();
            assertThat(mysteryCase.getStatus()).isEqualTo(CaseStatus.PUBLISHED);
            assertThat(mysteryCase.getKeywordTags()).containsExactly("저택", "밀실", "알리바이");
            assertThat(mysteryCase.getIntroduction()).isNotBlank();
            assertThat(mysteryCase.getTrickSummary()).isNotBlank();
            assertThat(mysteryCase.getRevealExplanation()).isNotBlank();
            assertThat(mysteryCase.getSourceNovelTitle()).isEqualTo("The Last Chime");
            assertThat(mysteryCase.getGlossary()).hasSize(4);
            assertThat(mysteryCase.getContentUpdatedAt()).isNotNull();
            assertThat(mysteryCase.getTestVerifiedAt()).isNotNull();
            assertThat(mysteryCase.getCulpritSuspect().getName()).isEqualTo("해리스");

            List<Suspect> suspects = listByCase(Suspect.class, caseId);
            assertThat(suspects)
                    .extracting(Suspect::getName, Suspect::isCulprit, Suspect::isCanConverse, Suspect::getSpriteKey)
                    .containsExactly(
                            tuple("해리스", true, true, "npc-butler-01"),
                            tuple("마거릿", false, true, "npc-housekeeper-01"),
                            tuple("톰", false, true, "npc-gardener-01"),
                            tuple("엘리스", false, false, null));
            assertThat(suspects).allSatisfy(suspect -> assertThat(suspect.getPersonaPrompt()).isNull());

            List<Evidence> evidences = listByCase(Evidence.class, caseId);
            assertThat(evidences).extracting(Evidence::getName).containsExactly("서재의 작은 상자", "찢긴 메모");

            List<CaseTrickElement> elements = listByCase(CaseTrickElement.class, caseId);
            assertThat(elements).hasSize(1);
            GradingRubric rubric = elements.getFirst().getGradingRubric();
            assertThat(rubric.items()).extracting("itemId").containsExactly("trk-1-i1", "trk-1-i2");
            assertThat(rubric.easyFeedback().evidenceIds()).containsExactly(evidences.get(0).getId());
            assertThat(rubric.easyFeedback().suspectIds()).containsExactly(suspects.get(0).getId());

            CaseMap caseMap = listByCase(CaseMap.class, caseId).getFirst();
            assertThat(caseMap.getMapUrl()).isEqualTo("/assets/maps/clocktower-mansion.json");
            assertThat(caseMap.getCollisionLayer()).isEqualTo("collision");

            List<MapHotspot> hotspots = listByCase(MapHotspot.class, caseId);
            assertThat(hotspots)
                    .extracting(MapHotspot::getType, MapHotspot::getRefId)
                    .containsExactlyInAnyOrder(
                            tuple(HotspotType.SUSPECT, suspects.get(0).getId()),
                            tuple(HotspotType.SUSPECT, suspects.get(1).getId()),
                            tuple(HotspotType.SUSPECT, suspects.get(2).getId()),
                            tuple(HotspotType.EVIDENCE, evidences.get(0).getId()),
                            tuple(HotspotType.EVIDENCE, evidences.get(1).getId()));

            Draft draft = listByCase(CaseDraft.class, caseId).getFirst().getDraftJson();
            assertThat(draft.suspects()).extracting(Draft.SuspectItem::entityId)
                    .containsExactlyElementsOf(suspects.stream().map(Suspect::getId).toList());
            assertThat(draft.evidences()).extracting(Draft.EvidenceItem::entityId)
                    .containsExactlyElementsOf(evidences.stream().map(Evidence::getId).toList());
            assertThat(draft.trick().elements()).extracting(Draft.TrickElementItem::entityId)
                    .containsExactly(elements.getFirst().getId());
            assertThat(draft.suspects().getFirst().persona().speechStyle()).isNotBlank();
        });
    }

    @Test
    void loadAll_두번실행_사건1건() throws IOException {

        // given
        String title = writeSeed("clocktower.json", seed -> { });
        devSeedService.loadAll(seedPath());

        // when
        DevSeedLoadResult second = devSeedService.loadAll(seedPath());

        // then
        assertThat(second).isEqualTo(new DevSeedLoadResult(0, 1, 0));
        transactionTemplate.executeWithoutResult(status -> assertThat(findPublishedCases(title)).hasSize(1));
    }

    @Test
    void loadAll_잘못된시드_그파일만건너뜀() throws IOException {

        // given
        String twoCulprits = writeSeed("1-two-culprits.json",
                seed -> ((ObjectNode) array(seed, "suspects").get(1)).put("isCulprit", true));
        String unknownHotspotKey = writeSeed("2-unknown-key.json",
                seed -> ((ObjectNode) array(object(seed, "map"), "hotspots").get(0)).put("key", "sus-99"));
        String unknownFeedbackKey = writeSeed("3-unknown-feedback-key.json",
                seed -> {
                    ObjectNode element = (ObjectNode) array(object(seed, "trick"), "elements").get(0);
                    array(object(object(element, "rubric"), "easyFeedback"), "evidenceKeys").add("evi-99");
                });
        String unplacedEvidence = writeSeed("4-unplaced-evidence.json",
                seed -> array(object(seed, "map"), "hotspots").remove(4));
        Files.writeString(seedDir.resolve("5-broken.json"), "{ \"schemaVersion\": 1, ");
        String valid = writeSeed("6-valid.json", seed -> { });

        // when
        DevSeedLoadResult result = devSeedService.loadAll(seedPath());

        // then
        assertThat(result).isEqualTo(new DevSeedLoadResult(1, 0, 5));
        transactionTemplate.executeWithoutResult(status -> {
            assertThat(findPublishedCases(twoCulprits)).isEmpty();
            assertThat(findPublishedCases(unknownHotspotKey)).isEmpty();
            assertThat(findPublishedCases(unknownFeedbackKey)).isEmpty();
            assertThat(findPublishedCases(unplacedEvidence)).isEmpty();
            assertThat(findPublishedCases(valid)).hasSize(1);
        });
    }

    @Test
    void loadAll_폴더없음_반영없이종료() {

        // given
        String missing = seedDir.resolve("missing").toUri().toString();

        // when
        DevSeedLoadResult fileResult = devSeedService.loadAll(missing);
        DevSeedLoadResult classpathResult = devSeedService.loadAll("classpath:dev-data-missing/");

        // then
        assertThat(fileResult).isEqualTo(new DevSeedLoadResult(0, 0, 0));
        assertThat(classpathResult).isEqualTo(new DevSeedLoadResult(0, 0, 0));
    }

    /**
     * 예시 시드를 표시명만 고유하게 바꾸고 modifier를 적용해 임시 폴더에 쓴다
     *
     * @return 바꾼 표시명
     */
    private String writeSeed(String fileName, Consumer<ObjectNode> modifier) throws IOException {
        ObjectNode seed;
        try (InputStream inputStream = new ClassPathResource(SAMPLE_SEED).getInputStream()) {
            seed = (ObjectNode) JSON_MAPPER.readTree(inputStream);
        }
        String title = "시드 테스트 " + UUID.randomUUID();
        object(seed, "card").put("displayTitle", title);
        modifier.accept(seed);
        Files.writeString(seedDir.resolve(fileName), JSON_MAPPER.writeValueAsString(seed));

        return title;
    }

    private static ObjectNode object(ObjectNode node, String field) {
        return (ObjectNode) node.get(field);
    }

    private static ArrayNode array(ObjectNode node, String field) {
        return (ArrayNode) node.get(field);
    }

    private String seedPath() {
        return seedDir.toUri().toString();
    }

    private List<MysteryCase> findPublishedCases(String title) {
        return entityManager.createQuery(
                        "select c from MysteryCase c where c.displayTitle = :title and c.status = :status", MysteryCase.class)
                .setParameter("title", title)
                .setParameter("status", CaseStatus.PUBLISHED)
                .getResultList();
    }

    private <T> List<T> listByCase(Class<T> type, Long caseId) {
        return entityManager.createQuery(
                        "select e from " + type.getSimpleName() + " e where e.mysteryCase.id = :caseId order by e.id", type)
                .setParameter("caseId", caseId)
                .getResultList();
    }
}
