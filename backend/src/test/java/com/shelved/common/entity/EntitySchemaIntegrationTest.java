package com.shelved.common.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shelved.IntegrationTestSupport;
import com.shelved.common.entity.json.AiRequestInput;
import com.shelved.common.enums.AiRequestType;
import com.shelved.common.enums.LlmCallStatus;
import com.shelved.common.enums.LlmRequestType;
import com.shelved.common.enums.UserRole;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

class EntitySchemaIntegrationTest extends IntegrationTestSupport {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 5, 14, 0);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void createSchema_users테이블_googleSub와nicknameLower에UNIQUE제약생성() {

        // given
        String table = "users";

        // when
        Map<String, String> uniqueConstraints = findUniqueConstraints(table);

        // then
        assertThat(uniqueConstraints)
                .containsEntry("uk_users_google_sub", "UNIQUE (google_sub)")
                .containsEntry("uk_users_nickname_lower", "UNIQUE (nickname_lower)");
        assertThat(findNullableColumns(table))
                .containsEntry("google_sub", "NO")
                .containsEntry("role", "NO")
                .containsEntry("nickname", "YES")
                .containsEntry("nickname_lower", "YES");
    }

    @Test
    void createSchema_refreshTokens테이블_tokenHash에UNIQUE와users외래키생성() {

        // given
        String table = "refresh_tokens";

        // when
        Map<String, String> uniqueConstraints = findUniqueConstraints(table);
        List<String> foreignKeys = jdbcTemplate.queryForList(
                "select pg_get_constraintdef(oid) from pg_constraint where conrelid = ?::regclass and contype = 'f'",
                String.class, table);

        // then
        assertThat(uniqueConstraints).containsEntry("uk_refresh_tokens_token_hash", "UNIQUE (token_hash)");
        assertThat(foreignKeys).containsExactly("FOREIGN KEY (user_id) REFERENCES users(id)");
        assertThat(findNullableColumns(table))
                .containsEntry("user_id", "NO")
                .containsEntry("token_hash", "NO")
                .containsEntry("expires_at", "NO")
                .containsEntry("revoked_at", "YES");
    }

    @Test
    void updateNickname_대소문자만다른닉네임_UNIQUE위반() {

        // given
        transactionTemplate.executeWithoutResult(status -> {
            User user = new User("google-sub-a", UserRole.PLAYER);
            user.updateNickname("Detective");
            entityManager.persist(user);
        });

        // when & then
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            User user = new User("google-sub-b", UserRole.PLAYER);
            user.updateNickname("DETECTIVE");
            entityManager.persist(user);
            entityManager.flush();
        })).rootCause().hasMessageContaining("uk_users_nickname_lower");
    }

    @Test
    void createSchema_전체테이블_UNIQUE제약생성() {

        // given
        Map<String, Map<String, String>> expected = Map.ofEntries(
                Map.entry("cases", Map.of()),
                Map.entry("suspects", Map.of()),
                Map.entry("evidences", Map.of()),
                Map.entry("case_trick_elements", Map.of(
                        "uk_case_trick_elements_case_id_order_index", "UNIQUE (case_id, order_index)")),
                Map.entry("map_hotspots", Map.of(
                        "uk_map_hotspots_case_id_type_ref_id", "UNIQUE (case_id, type, ref_id)")),
                Map.entry("case_maps", Map.of("uk_case_maps_case_id", "UNIQUE (case_id)")),
                Map.entry("case_source_texts", Map.of("uk_case_source_texts_case_id", "UNIQUE (case_id)")),
                Map.entry("case_drafts", Map.of("uk_case_drafts_case_id", "UNIQUE (case_id)")),
                Map.entry("admin_case_messages", Map.of(
                        "uk_admin_case_messages_case_id_request_id_sender_type",
                        "UNIQUE (case_id, request_id, sender_type)")),
                Map.entry("rooms", Map.of()),
                Map.entry("room_participants", Map.of(
                        "uk_room_participants_room_id_user_id", "UNIQUE (room_id, user_id)")),
                Map.entry("npc_conversations", Map.of(
                        "uk_npc_conversations_room_id_suspect_id", "UNIQUE (room_id, suspect_id)")),
                Map.entry("npc_messages", Map.of(
                        "uk_npc_messages_conversation_id_sequence_no", "UNIQUE (conversation_id, sequence_no)")),
                Map.entry("player_notes", Map.of(
                        "uk_player_notes_room_id_user_id", "UNIQUE (room_id, user_id)")),
                Map.entry("hints", Map.of("uk_hints_room_id_request_id", "UNIQUE (room_id, request_id)")),
                Map.entry("hint_followups", Map.of("uk_hint_followups_hint_id", "UNIQUE (hint_id)")),
                Map.entry("accusations", Map.of(
                        "uk_accusations_room_id_request_id", "UNIQUE (room_id, request_id)")),
                Map.entry("play_results", Map.of("uk_play_results_room_id", "UNIQUE (room_id)")),
                Map.entry("ai_requests", Map.of(
                        "uk_ai_requests_room_id_request_id", "UNIQUE (room_id, request_id)")),
                Map.entry("evidence_inspections", Map.of(
                        "uk_evidence_inspections_room_id_evidence_id", "UNIQUE (room_id, evidence_id)")),
                Map.entry("llm_usage_logs", Map.of()),
                Map.entry("llm_budget_settings", Map.of())
        );

        // when
        Map<String, Map<String, String>> actual = expected.keySet().stream()
                .collect(Collectors.toMap(table -> table, this::findUniqueConstraints));

        // then
        SoftAssertions softly = new SoftAssertions();
        expected.forEach((table, constraints) ->
                softly.assertThat(actual.get(table)).as(table).isEqualTo(constraints));
        softly.assertAll();
    }

    @Test
    void createSchema_전체테이블_외래키생성() {

        // given
        Map<String, List<String>> expected = Map.ofEntries(
                Map.entry("cases", List.of(
                        "FOREIGN KEY (culprit_suspect_id) REFERENCES suspects(id)",
                        "FOREIGN KEY (cloned_from_case_id) REFERENCES cases(id)")),
                Map.entry("suspects", List.of(fkToCases())),
                Map.entry("evidences", List.of(fkToCases())),
                Map.entry("case_trick_elements", List.of(fkToCases())),
                // ref_id는 폴리모픽 참조이므로 외래키가 없다
                Map.entry("map_hotspots", List.of(fkToCases())),
                Map.entry("case_maps", List.of(fkToCases())),
                Map.entry("case_source_texts", List.of(fkToCases())),
                Map.entry("case_drafts", List.of(fkToCases())),
                Map.entry("admin_case_messages", List.of(fkToCases())),
                Map.entry("rooms", List.of(
                        fkToCases(),
                        "FOREIGN KEY (host_user_id) REFERENCES users(id)")),
                Map.entry("room_participants", List.of(
                        fkToRooms(),
                        "FOREIGN KEY (user_id) REFERENCES users(id)")),
                Map.entry("npc_conversations", List.of(
                        fkToRooms(),
                        "FOREIGN KEY (suspect_id) REFERENCES suspects(id)")),
                Map.entry("npc_messages", List.of(
                        "FOREIGN KEY (conversation_id) REFERENCES npc_conversations(id)",
                        "FOREIGN KEY (sender_user_id) REFERENCES users(id)")),
                Map.entry("player_notes", List.of(
                        fkToRooms(),
                        "FOREIGN KEY (user_id) REFERENCES users(id)")),
                Map.entry("hints", List.of(
                        fkToRooms(),
                        "FOREIGN KEY (requested_by_user_id) REFERENCES users(id)")),
                Map.entry("hint_followups", List.of(
                        "FOREIGN KEY (hint_id) REFERENCES hints(id)",
                        "FOREIGN KEY (requested_by_user_id) REFERENCES users(id)")),
                Map.entry("accusations", List.of(
                        fkToRooms(),
                        "FOREIGN KEY (submitted_by_user_id) REFERENCES users(id)",
                        "FOREIGN KEY (suspect_id) REFERENCES suspects(id)")),
                Map.entry("play_results", List.of(fkToRooms(), fkToCases())),
                Map.entry("ai_requests", List.of(fkToRooms())),
                Map.entry("evidence_inspections", List.of(
                        fkToRooms(),
                        "FOREIGN KEY (evidence_id) REFERENCES evidences(id)")),
                Map.entry("llm_usage_logs", List.of(
                        "FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE SET NULL",
                        fkToCases())),
                Map.entry("llm_budget_settings", List.of())
        );

        // when
        Map<String, List<String>> actual = expected.keySet().stream()
                .collect(Collectors.toMap(table -> table, this::findForeignKeys));

        // then
        SoftAssertions softly = new SoftAssertions();
        expected.forEach((table, foreignKeys) ->
                softly.assertThat(actual.get(table)).as(table).containsExactlyInAnyOrderElementsOf(foreignKeys));
        softly.assertAll();
    }

    @Test
    void createSchema_jsonb컬럼_jsonb타입으로생성() {

        // given
        List<String> jsonbColumns = List.of(
                "cases.keyword_tags", "cases.glossary", "case_trick_elements.grading_rubric",
                "case_drafts.draft_json", "admin_case_messages.tool_changes", "admin_case_messages.input_info",
                "admin_case_messages.response_json", "accusations.element_results",
                "ai_requests.input_json", "ai_requests.result_json");

        // when
        List<String> actual = jdbcTemplate.queryForList(
                "select table_name || '.' || column_name from information_schema.columns "
                        + "where table_schema = current_schema() and data_type = 'jsonb'",
                String.class);

        // then
        assertThat(actual).containsExactlyInAnyOrderElementsOf(jsonbColumns);
    }

    @Test
    void createAiRequest_같은세션의같은requestId_UNIQUE위반() {

        // given
        Room room = transactionTemplate.execute(status -> persistRoom());
        String requestId = UUID.randomUUID().toString();
        transactionTemplate.executeWithoutResult(status -> entityManager.persist(newAiRequest(room, requestId)));

        // when & then
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            entityManager.persist(newAiRequest(room, requestId));
            entityManager.flush();
        })).rootCause().hasMessageContaining("uk_ai_requests_room_id_request_id");
    }

    @Test
    void createHintFollowup_같은힌트에두번째보충질문_UNIQUE위반() {

        // given
        Hint hint = transactionTemplate.execute(status -> {
            Room room = persistRoom();
            Hint saved = new Hint(room, room.getHostUser(), UUID.randomUUID().toString(), "질문", "답변", NOW);
            entityManager.persist(saved);
            entityManager.persist(newFollowup(saved));
            return saved;
        });

        // when & then
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            entityManager.persist(newFollowup(entityManager.merge(hint)));
            entityManager.flush();
        })).rootCause().hasMessageContaining("uk_hint_followups_hint_id");
    }

    @Test
    void createPlayResult_같은세션에두번째결과_UNIQUE위반() {

        // given
        Room room = transactionTemplate.execute(status -> {
            Room saved = persistRoom();
            entityManager.persist(new PlayResult(saved, saved.getMysteryCase(), true, 600, 1, NOW));
            return saved;
        });

        // when & then
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            Room managed = entityManager.merge(room);
            entityManager.persist(new PlayResult(managed, managed.getMysteryCase(), false, 700, 2, NOW));
            entityManager.flush();
        })).rootCause().hasMessageContaining("uk_play_results_room_id");
    }

    @Test
    void createAdminCaseMessage_같은요청의관리자와AI발화_저장되고같은발화자중복은UNIQUE위반() {

        // given
        MysteryCase mysteryCase = transactionTemplate.execute(status -> {
            MysteryCase saved = new MysteryCase(NOW);
            entityManager.persist(saved);
            return saved;
        });
        String requestId = UUID.randomUUID().toString();

        // when
        transactionTemplate.executeWithoutResult(status -> {
            MysteryCase managed = entityManager.merge(mysteryCase);
            entityManager.persist(AdminCaseMessage.ofAdmin(managed, requestId, "인물을 정리해 줘", 0L, NOW));
            entityManager.persist(AdminCaseMessage.ofAi(managed, requestId, "정리했습니다", List.of(), null, 0L, NOW));
        });

        // then
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from admin_case_messages where request_id = ?", Integer.class, requestId);
        assertThat(count).isEqualTo(2);
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            MysteryCase managed = entityManager.merge(mysteryCase);
            entityManager.persist(AdminCaseMessage.ofAdmin(managed, requestId, "다시 보냄", 0L, NOW));
            entityManager.flush();
        })).rootCause().hasMessageContaining("uk_admin_case_messages_case_id_request_id_sender_type");
    }

    @Test
    void deleteRoom_사용량로그가참조하는세션삭제_로그는남고roomId만null() {

        // given
        Long[] ids = transactionTemplate.execute(status -> {
            Room room = persistRoom();
            LlmUsageLog log = new LlmUsageLog(LlmRequestType.NPC_CHAT, "test-model", room, room.getMysteryCase(),
                    1200, 300, new BigDecimal("0.004500"), LlmCallStatus.SUCCEEDED, null, NOW);
            entityManager.persist(log);
            return new Long[]{room.getId(), log.getId()};
        });
        Long roomId = ids[0];
        Long logId = ids[1];

        // when
        transactionTemplate.executeWithoutResult(status -> entityManager.remove(entityManager.find(Room.class, roomId)));

        // then
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select room_id, case_id, input_tokens from llm_usage_logs where id = ?", logId);
        assertThat(row.get("room_id")).isNull();
        assertThat(row.get("case_id")).isNotNull();
        assertThat(row.get("input_tokens")).isEqualTo(1200);
    }

    @Test
    void createLlmUsageLog_사용량미확인_토큰과비용null로저장() {

        // given & when
        Long logId = transactionTemplate.execute(status -> {
            LlmUsageLog log = new LlmUsageLog(LlmRequestType.QUESTION_FILTER, "test-filter-model", null, null,
                    null, null, null, LlmCallStatus.FAILED, "TIMEOUT", NOW);
            entityManager.persist(log);
            return log.getId();
        });

        // then
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select usage_known, input_tokens, output_tokens, estimated_cost from llm_usage_logs where id = ?",
                logId);
        assertThat(row.get("usage_known")).isEqualTo(false);
        assertThat(row.get("input_tokens")).isNull();
        assertThat(row.get("output_tokens")).isNull();
        assertThat(row.get("estimated_cost")).isNull();
    }

    private Room persistRoom() {
        User user = new User("google-sub-" + UUID.randomUUID(), UserRole.PLAYER);
        entityManager.persist(user);
        MysteryCase mysteryCase = new MysteryCase(NOW);
        entityManager.persist(mysteryCase);
        Room room = new Room(mysteryCase, user, false, 1200, NOW);
        entityManager.persist(room);
        return room;
    }

    private AiRequest newAiRequest(Room room, String requestId) {
        return new AiRequest(entityManager.merge(room), requestId, AiRequestType.HINT, null,
                AiRequestInput.question("누구 진술부터 확인해야 할까요?"), NOW);
    }

    private HintFollowup newFollowup(Hint hint) {
        return new HintFollowup(hint, hint.getRequestedByUser(), UUID.randomUUID().toString(),
                "무슨 뜻인가요?", "시각을 나란히 적어 보세요.", NOW);
    }

    private static String fkToCases() {
        return "FOREIGN KEY (case_id) REFERENCES cases(id)";
    }

    private static String fkToRooms() {
        return "FOREIGN KEY (room_id) REFERENCES rooms(id)";
    }

    private List<String> findForeignKeys(String table) {
        return jdbcTemplate.queryForList(
                "select pg_get_constraintdef(oid) from pg_constraint where conrelid = ?::regclass and contype = 'f'",
                String.class, table);
    }

    private Map<String, String> findUniqueConstraints(String table) {
        return jdbcTemplate.query(
                        "select conname, pg_get_constraintdef(oid) as def from pg_constraint where conrelid = ?::regclass and contype = 'u'",
                        (rs, rowNum) -> Map.entry(rs.getString("conname"), rs.getString("def")),
                        table)
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Map<String, String> findNullableColumns(String table) {
        return jdbcTemplate.query(
                        "select column_name, is_nullable from information_schema.columns where table_name = ?",
                        (rs, rowNum) -> Map.entry(rs.getString("column_name"), rs.getString("is_nullable")),
                        table)
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

}
