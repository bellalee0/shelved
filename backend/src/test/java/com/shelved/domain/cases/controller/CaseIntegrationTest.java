package com.shelved.domain.cases.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shelved.IntegrationTestSupport;
import com.shelved.common.entity.MysteryCase;
import com.shelved.common.enums.Difficulty;
import com.shelved.fixture.ClocktowerCaseFixture;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class CaseIntegrationTest extends IntegrationTestSupport {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 5, 14, 0);
    private static final Set<String> CARD_FIELDS = Set.of(
            "caseId", "displayTitle", "difficulty", "keywordTags", "coverImageUrl",
            "timeLimitSec", "hintLimit", "accusationLimit");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void getCaseList_비로그인_공개사건만반환() throws Exception {

        // given
        Long publishedId = transactionTemplate.execute(status ->
                ClocktowerCaseFixture.persistPublished(entityManager, NOW).mysteryCase().getId());
        Long draftId = transactionTemplate.execute(status -> {
            MysteryCase draft = new MysteryCase(NOW);
            draft.updateCardInfo("초안 사건 " + UUID.randomUUID(), Difficulty.HARD, List.of("초안"));
            entityManager.persist(draft);
            return draft.getId();
        });

        // when
        ResultActions result = mockMvc.perform(get("/api/cases"));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("사건 목록 조회 성공"))
                .andExpect(jsonPath("$.data[?(@.caseId == %d)].displayTitle", publishedId)
                        .value(ClocktowerCaseFixture.DISPLAY_TITLE))
                .andExpect(jsonPath("$.data[?(@.caseId == %d)].difficulty", publishedId).value("EASY"))
                .andExpect(jsonPath("$.data[?(@.caseId == %d)].timeLimitSec", publishedId).value(1200))
                .andExpect(jsonPath("$.data[?(@.caseId == %d)].hintLimit", publishedId).value(5))
                .andExpect(jsonPath("$.data[?(@.caseId == %d)].accusationLimit", publishedId).value(3))
                .andExpect(jsonPath("$.data[?(@.caseId == %d)]", draftId).isEmpty());
    }

    @Test
    void getCaseList_정답필드미포함() throws Exception {

        // given
        transactionTemplate.executeWithoutResult(status -> ClocktowerCaseFixture.persistPublished(entityManager, NOW));

        // when
        String body = mockMvc.perform(get("/api/cases"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // then: 카드 필드만 있고 원작·범인·트릭·리빌·용어집 값이 없다
        JsonNode data = JsonMapper.builder().build().readTree(body).get("data");
        assertThat(data.size()).isPositive();
        data.forEach(card -> assertThat(card.propertyNames()).containsExactlyInAnyOrderElementsOf(CARD_FIELDS));
        assertThat(body).doesNotContain(
                ClocktowerCaseFixture.SOURCE_NOVEL_TITLE,
                ClocktowerCaseFixture.SOURCE_NOVEL_AUTHOR,
                ClocktowerCaseFixture.TRICK_SUMMARY,
                ClocktowerCaseFixture.REVEAL_EXPLANATION,
                ClocktowerCaseFixture.INTRODUCTION,
                "해리스");
    }
}
