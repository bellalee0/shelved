package com.shelved.common.entity.json;

import static org.assertj.core.api.Assertions.assertThat;

import com.shelved.IntegrationTestSupport;
import com.shelved.common.entity.Accusation;
import com.shelved.common.entity.AdminCaseMessage;
import com.shelved.common.entity.AiRequest;
import com.shelved.common.entity.CaseDraft;
import com.shelved.common.entity.CaseTrickElement;
import com.shelved.common.entity.MysteryCase;
import com.shelved.common.entity.Room;
import com.shelved.common.entity.User;
import com.shelved.common.enums.AiRequestType;
import com.shelved.common.enums.ChatInputMode;
import com.shelved.common.enums.RubricItemResult;
import com.shelved.common.enums.UserRole;
import com.shelved.common.enums.Verdict;
import com.shelved.fixture.ClocktowerCaseFixture;
import com.shelved.fixture.ClocktowerCaseFixture.SampleCase;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * jsonb 컬럼 ↔ record·JsonNode 변환 (Hibernate Jackson3JsonFormatMapper) 저장 후 조회 확인
 */
class JsonbMappingIntegrationTest extends IntegrationTestSupport {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 5, 14, 0);

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void caseDraft_전체초안저장후조회_같은값() {

        // given
        Draft draft = ClocktowerCaseFixture.draft();
        Long draftId = transactionTemplate.execute(status -> {
            MysteryCase mysteryCase = new MysteryCase(NOW);
            entityManager.persist(mysteryCase);
            CaseDraft caseDraft = new CaseDraft(mysteryCase, draft, NOW);
            entityManager.persist(caseDraft);
            return caseDraft.getId();
        });

        // when
        Draft found = transactionTemplate.execute(status -> entityManager.find(CaseDraft.class, draftId).getDraftJson());

        // then
        assertThat(found).isEqualTo(draft);
        // 초안 JSON은 API 응답·AI tool과 같은 키를 써야 한다 (admin-draft-schema.md)
        assertThat(jdbcTemplate.queryForObject(
                "select draft_json -> 'suspects' -> 0 ->> 'isCulprit' from case_drafts where id = ?",
                String.class, draftId)).isEqualTo("true");
        assertThat(jdbcTemplate.queryForObject(
                "select draft_json -> 'trick' -> 'elements' -> 0 -> 'rubric' -> 'easyFeedback' ->> 'evidenceKeys' "
                        + "from case_drafts where id = ?",
                String.class, draftId)).isEqualTo("[\"evi-1\"]");
    }

    @Test
    void caseDraft_빈초안저장후조회_스키마버전1과null및빈배열() {

        // given
        Long draftId = transactionTemplate.execute(status -> {
            MysteryCase mysteryCase = new MysteryCase(NOW);
            entityManager.persist(mysteryCase);
            CaseDraft caseDraft = CaseDraft.empty(mysteryCase, NOW);
            entityManager.persist(caseDraft);
            return caseDraft.getId();
        });

        // when
        Draft found = transactionTemplate.execute(status -> entityManager.find(CaseDraft.class, draftId).getDraftJson());
        String raw = jdbcTemplate.queryForObject("select draft_json::text from case_drafts where id = ?",
                String.class, draftId);

        // then
        assertThat(found).isEqualTo(Draft.empty());
        assertThat(jsonMapper.readTree(raw)).isEqualTo(jsonMapper.readTree("""
                {
                  "schemaVersion": 1,
                  "card": { "displayTitle": null, "difficulty": null, "keywordTags": [] },
                  "source": { "novelTitle": null, "novelAuthor": null, "novelIntro": null },
                  "briefing": { "introduction": null, "investigationGoal": null, "backgroundKnowledge": null },
                  "suspects": [],
                  "evidences": [],
                  "trick": { "summary": null, "elements": [] },
                  "revealExplanation": null,
                  "glossary": []
                }
                """));
    }

    @Test
    void sampleCase_keywordTags와glossary와gradingRubric저장후조회_같은값() {

        // given
        SampleCase sample = transactionTemplate.execute(status -> ClocktowerCaseFixture.persistPublished(entityManager, NOW));
        GradingRubric expectedRubric = sample.trickElement().getGradingRubric();

        // when
        MysteryCase foundCase = transactionTemplate.execute(status ->
                entityManager.find(MysteryCase.class, sample.mysteryCase().getId()));
        GradingRubric foundRubric = transactionTemplate.execute(status ->
                entityManager.find(CaseTrickElement.class, sample.trickElement().getId()).getGradingRubric());

        // then
        assertThat(foundCase.getKeywordTags()).isEqualTo(ClocktowerCaseFixture.KEYWORD_TAGS);
        assertThat(foundCase.getGlossary()).isEqualTo(ClocktowerCaseFixture.GLOSSARY);
        assertThat(foundRubric).isEqualTo(expectedRubric);
        assertThat(foundRubric.easyFeedback().evidenceIds()).containsExactly(sample.smallBox().getId());
        assertThat(foundRubric.easyFeedback().suspectIds()).containsExactly(sample.butler().getId());
    }

    @Test
    void adminCaseMessage_toolChanges와inputInfo와responseJson저장후조회_같은값() {

        // given
        List<ToolChange> toolChanges = List.of(
                new ToolChange("add_suspect", "sus-1", "인물 sus-1 추가"),
                new ToolChange("set_card_info", null, "카드 정보 변경"));
        InputInfo inputInfo = new InputInfo(ChatInputMode.HISTORY_SUMMARIZED, 120000, "1~10번째 대화 요약");
        JsonNode responseJson = jsonMapper.readTree("""
                { "requestId": "r-1", "draftIssues": [], "aiMessage": { "content": "정리했습니다", "inputInfo": null } }
                """);
        String requestId = UUID.randomUUID().toString();
        Long[] ids = transactionTemplate.execute(status -> {
            MysteryCase mysteryCase = new MysteryCase(NOW);
            entityManager.persist(mysteryCase);
            AdminCaseMessage admin = AdminCaseMessage.ofAdmin(mysteryCase, requestId, "인물을 정리해 줘", 3L, NOW);
            AdminCaseMessage ai = AdminCaseMessage.ofAi(mysteryCase, requestId, "정리했습니다", toolChanges, inputInfo, 3L, NOW);
            entityManager.persist(admin);
            entityManager.persist(ai);
            admin.markSucceeded(responseJson);
            return new Long[]{admin.getId(), ai.getId()};
        });

        // when
        AdminCaseMessage foundAdmin = transactionTemplate.execute(status ->
                entityManager.find(AdminCaseMessage.class, ids[0]));
        AdminCaseMessage foundAi = transactionTemplate.execute(status ->
                entityManager.find(AdminCaseMessage.class, ids[1]));

        // then
        assertThat(foundAdmin.getToolChanges()).isEmpty();
        assertThat(foundAdmin.getInputInfo()).isNull();
        assertThat(foundAdmin.getResponseJson()).isEqualTo(responseJson);
        assertThat(foundAi.getToolChanges()).isEqualTo(toolChanges);
        assertThat(foundAi.getInputInfo()).isEqualTo(inputInfo);
        assertThat(foundAi.getResponseJson()).isNull();
    }

    @Test
    void aiRequestAndAccusation_inputJson과resultJson과elementResults저장후조회_같은값() {

        // given
        SampleCase sample = transactionTemplate.execute(status -> ClocktowerCaseFixture.persistPublished(entityManager, NOW));
        AiRequestInput input = AiRequestInput.accusation(sample.butler().getId(), "해리스가 녹음기를 사용했다.");
        JsonNode resultJson = jsonMapper.readTree("""
                { "accusation": { "accusationId": 5, "verdict": "PARTIAL" }, "roomStatus": "IN_PROGRESS" }
                """);
        List<ElementResult> elementResults = List.of(
                new ElementResult(sample.trickElement().getId(), "trk-1-i1", RubricItemResult.MET, "녹음기", "도구를 밝힘"),
                new ElementResult(sample.trickElement().getId(), "trk-1-i2", RubricItemResult.MISSING, null, "목적 없음"));
        Long[] ids = transactionTemplate.execute(status -> {
            User user = new User("google-sub-" + UUID.randomUUID(), UserRole.PLAYER);
            entityManager.persist(user);
            MysteryCase mysteryCase = entityManager.merge(sample.mysteryCase());
            Room room = new Room(mysteryCase, user, false, 1200, NOW);
            entityManager.persist(room);
            String requestId = UUID.randomUUID().toString();
            AiRequest aiRequest = new AiRequest(room, requestId, AiRequestType.ACCUSATION, null, input, NOW);
            entityManager.persist(aiRequest);
            Accusation accusation = new Accusation(room, user, entityManager.merge(sample.butler()), requestId,
                    input.trickExplanation(), NOW, 431250L);
            entityManager.persist(accusation);
            accusation.completeGrading(true, Verdict.PARTIAL, elementResults, "녹음기를 짚은 것은 맞아요.");
            aiRequest.succeed(accusation.getId(), resultJson, NOW.plusSeconds(8));
            return new Long[]{aiRequest.getId(), accusation.getId()};
        });

        // when
        AiRequest foundRequest = transactionTemplate.execute(status -> entityManager.find(AiRequest.class, ids[0]));
        Accusation foundAccusation = transactionTemplate.execute(status -> entityManager.find(Accusation.class, ids[1]));

        // then
        assertThat(foundRequest.getInputJson()).isEqualTo(input);
        assertThat(foundRequest.getResultJson()).isEqualTo(resultJson);
        assertThat(foundAccusation.getElementResults()).isEqualTo(elementResults);
        // 입력 유형에 없는 필드는 저장하지 않는다
        assertThat(jsonMapper.readTree(jdbcTemplate.queryForObject(
                "select input_json::text from ai_requests where id = ?", String.class, ids[0])))
                .isEqualTo(jsonMapper.readTree("{ \"suspectId\": %d, \"trickExplanation\": \"해리스가 녹음기를 사용했다.\" }"
                        .formatted(sample.butler().getId())));
    }
}
