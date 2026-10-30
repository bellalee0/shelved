package com.shelved.common.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shelved.IntegrationTestSupport;
import com.shelved.common.enums.UserRole;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

class EntitySchemaIntegrationTest extends IntegrationTestSupport {

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
