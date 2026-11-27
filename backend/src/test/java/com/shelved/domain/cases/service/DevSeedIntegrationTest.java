package com.shelved.domain.cases.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.shelved.IntegrationTestSupport;
import com.shelved.common.config.AssetsProperties;
import com.shelved.common.config.CasebuilderProperties;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.ResourcePatternResolver;
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
    private DevSeedImporter devSeedImporter;

    @Autowired
    private ResourcePatternResolver resourcePatternResolver;

    @Autowired
    private CasebuilderProperties casebuilderProperties;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @TempDir
    private Path seedDir;

    @TempDir
    private Path tempDir;

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
            assertThat(mysteryCase.getCoverImageUrl()).isNull();
            assertThat(mysteryCase.getContentUpdatedAt()).isNotNull();
            assertThat(mysteryCase.getTestVerifiedAt()).isNotNull();
            assertThat(mysteryCase.getCulpritSuspect().getName()).isEqualTo("해리스");

            List<Suspect> suspects = listByCase(Suspect.class, caseId);
            assertThat(suspects)
                    .extracting(Suspect::getName, Suspect::isCulprit, Suspect::isCanConverse, Suspect::getSpriteKey)
                    .containsExactly(
                            tuple("해리스", true, true, "npc-gentleman-01"),
                            tuple("마거릿", false, true, "npc-maid-01"),
                            tuple("톰", false, true, "npc-workman-01"),
                            tuple("엘리스", false, false, null));
            // persona가 있는 대화 가능 인물은 변환된 persona_prompt, persona가 없는 대화 불가 인물은 null
            assertThat(suspects.subList(0, 3)).allSatisfy(suspect -> assertThat(suspect.getPersonaPrompt()).isNotBlank());
            assertThat(suspects.get(0).getPersonaPrompt())
                    .contains("정중하고 짧게 말한다", "9시에는 주방에서 은식기를 닦고 있었다", "서재에서 녹음기를 설치했다")
                    .doesNotContain("2장 4문단", "20년째 근무");
            assertThat(suspects.get(3).getPersonaPrompt()).isNull();
            assertThat(suspects).extracting(Suspect::getPublicIntro)
                    .containsExactly("저택의 집사", "저택의 가정부", "저택의 정원사", "저택 주인의 주치의");

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
            assertThat(draft.suspects().getFirst().publicIntro()).isEqualTo("저택의 집사");
        });
    }

    @Test
    void loadAll_publicIntro없는기존형식시드_null로저장() throws IOException {

        // given: 모든 인물에서 publicIntro 필드를 제거한 기존 형식
        String title = writeSeed("legacy.json",
                seed -> array(seed, "suspects").forEach(node -> ((ObjectNode) node).remove("publicIntro")));

        // when
        DevSeedLoadResult result = devSeedService.loadAll(seedPath());

        // then
        assertThat(result).isEqualTo(new DevSeedLoadResult(1, 0, 0));
        transactionTemplate.executeWithoutResult(status -> {
            Long caseId = findPublishedCases(title).getFirst().getId();
            assertThat(listByCase(Suspect.class, caseId)).extracting(Suspect::getPublicIntro)
                    .containsOnlyNulls();
            Draft draft = listByCase(CaseDraft.class, caseId).getFirst().getDraftJson();
            assertThat(draft.suspects()).extracting(Draft.SuspectItem::publicIntro).containsOnlyNulls();
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
        String longIntro = writeSeed("7-long-intro.json",
                seed -> ((ObjectNode) array(seed, "suspects").get(0)).put("publicIntro", "가".repeat(201)));
        String blankIntro = writeSeed("8-blank-intro.json",
                seed -> ((ObjectNode) array(seed, "suspects").get(0)).put("publicIntro", " "));
        String emptyStatements = writeSeed("9-empty-statements.json",
                seed -> array(object((ObjectNode) array(seed, "suspects").get(1), "persona"), "statements").removeAll());
        String blankPressure = writeSeed("10-blank-pressure.json",
                seed -> object((ObjectNode) array(seed, "suspects").get(2), "persona").put("pressureResponse", " "));
        String conversableWithoutPersona = writeSeed("11-no-persona.json",
                seed -> ((ObjectNode) array(seed, "suspects").get(0)).putNull("persona"));
        Files.writeString(seedDir.resolve("5-broken.json"), "{ \"schemaVersion\": 1, ");
        String valid = writeSeed("6-valid.json", seed -> { });

        // when
        DevSeedLoadResult result = devSeedService.loadAll(seedPath());

        // then
        assertThat(result).isEqualTo(new DevSeedLoadResult(1, 0, 10));
        transactionTemplate.executeWithoutResult(status -> {
            assertThat(findPublishedCases(twoCulprits)).isEmpty();
            assertThat(findPublishedCases(unknownHotspotKey)).isEmpty();
            assertThat(findPublishedCases(unknownFeedbackKey)).isEmpty();
            assertThat(findPublishedCases(unplacedEvidence)).isEmpty();
            assertThat(findPublishedCases(longIntro)).isEmpty();
            assertThat(findPublishedCases(blankIntro)).isEmpty();
            assertThat(findPublishedCases(emptyStatements)).isEmpty();
            assertThat(findPublishedCases(blankPressure)).isEmpty();
            assertThat(findPublishedCases(conversableWithoutPersona)).isEmpty();
            assertThat(findPublishedCases(valid)).hasSize(1);
        });
    }

    @Test
    void loadAll_표지경로지정_에셋URL로저장() throws IOException {

        // given: 한도와 같은 크기의 파일까지 허용
        Path assetsDir = createAssetsDir();
        writeImage(assetsDir.resolve("covers/clocktower-sample.webp"), 16);
        writeImage(assetsDir.resolve("covers/limit.PNG"), maxCoverImageBytes());
        String webp = writeSeed("1-cover.json",
                seed -> seed.put("coverImagePath", "covers/clocktower-sample.webp"));
        String limit = writeSeed("2-limit.json", seed -> seed.put("coverImagePath", "covers/limit.PNG"));

        // when
        DevSeedLoadResult result = serviceWithAssets(assetsDir).loadAll(seedPath());

        // then
        assertThat(result).isEqualTo(new DevSeedLoadResult(2, 0, 0));
        transactionTemplate.executeWithoutResult(status -> {
            assertThat(findPublishedCases(webp).getFirst().getCoverImageUrl())
                    .isEqualTo("/assets/covers/clocktower-sample.webp");
            assertThat(findPublishedCases(limit).getFirst().getCoverImageUrl())
                    .isEqualTo("/assets/covers/limit.PNG");
        });
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  "})
    void loadAll_표지경로비어있음_null로저장(String coverImagePath) throws IOException {

        // given
        String title = writeSeed("no-cover.json", seed -> seed.put("coverImagePath", coverImagePath));

        // when
        DevSeedLoadResult result = devSeedService.loadAll(seedPath());

        // then
        assertThat(result).isEqualTo(new DevSeedLoadResult(1, 0, 0));
        transactionTemplate.executeWithoutResult(status ->
                assertThat(findPublishedCases(title).getFirst().getCoverImageUrl()).isNull());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "covers/../outside.webp",
            "covers/../covers/ok.webp",
            "covers/./ok.webp",
            "covers//ok.webp",
            "covers\\ok.webp",
            "/covers/ok.webp",
            "other/ok.webp",
            "https://example.com/covers/ok.webp",
            "covers/ok.gif",
            "covers/missing.webp",
            "covers/folder.webp",
            "covers/too-large.webp",
            "covers/link.webp",
            "covers/\u0000.webp"
    })
    void loadAll_허용하지않는표지경로_그파일만건너뜀(String coverImagePath) throws IOException {

        // given: 형식 검사만으로 거부되는지 확인하도록 대상 파일은 만들어 둔다
        Path assetsDir = createAssetsDir();
        writeImage(assetsDir.resolve("covers/ok.webp"), 16);
        writeImage(assetsDir.resolve("covers/ok.gif"), 16);
        writeImage(assetsDir.resolve("other/ok.webp"), 16);
        writeImage(assetsDir.resolve("outside.webp"), 16);
        writeImage(assetsDir.resolve("covers/too-large.webp"), maxCoverImageBytes() + 1);
        Files.createDirectory(assetsDir.resolve("covers/folder.webp"));
        Path outsideAssets = tempDir.resolve("secret.webp");
        writeImage(outsideAssets, 16);
        Files.createSymbolicLink(assetsDir.resolve("covers/link.webp"), outsideAssets);
        String invalid = writeSeed("1-invalid.json", seed -> seed.put("coverImagePath", coverImagePath));
        String valid = writeSeed("2-valid.json", seed -> seed.put("coverImagePath", "covers/ok.webp"));

        // when
        DevSeedLoadResult result = serviceWithAssets(assetsDir).loadAll(seedPath());

        // then
        assertThat(result).isEqualTo(new DevSeedLoadResult(1, 0, 1));
        transactionTemplate.executeWithoutResult(status -> {
            assertThat(findPublishedCases(invalid)).isEmpty();
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

    // 공개 저장소의 frontend/public/assets를 건드리지 않도록 임시 폴더를 에셋 폴더로 쓴다
    private Path createAssetsDir() throws IOException {
        return Files.createDirectories(tempDir.resolve("assets/covers")).getParent();
    }

    // 에셋 폴더만 바꾼 로더. 컨텍스트를 새로 만들지 않도록 직접 조립한다
    private DevSeedService serviceWithAssets(Path assetsDir) {
        DevSeedCoverImageChecker checker = new DevSeedCoverImageChecker(
                new AssetsProperties(assetsDir.toString()), casebuilderProperties);
        return new DevSeedService(resourcePatternResolver, devSeedImporter, checker);
    }

    private int maxCoverImageBytes() {
        return Math.toIntExact(casebuilderProperties.coverImageMaxSize().toBytes());
    }

    private static void writeImage(Path path, int size) throws IOException {
        Files.createDirectories(path.getParent());
        Files.write(path, new byte[size]);
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
