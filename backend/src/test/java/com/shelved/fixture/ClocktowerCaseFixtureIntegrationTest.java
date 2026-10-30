package com.shelved.fixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.shelved.IntegrationTestSupport;
import com.shelved.common.entity.CaseMap;
import com.shelved.common.entity.MapHotspot;
import com.shelved.common.entity.MysteryCase;
import com.shelved.common.entity.Suspect;
import com.shelved.common.enums.CaseStatus;
import com.shelved.common.enums.Difficulty;
import com.shelved.common.enums.HotspotType;
import com.shelved.fixture.ClocktowerCaseFixture.SampleCase;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

class ClocktowerCaseFixtureIntegrationTest extends IntegrationTestSupport {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 5, 14, 0);

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void persistPublished_가상샘플사건저장후조회_출시된사건과하위엔티티() {

        // given
        SampleCase sample = transactionTemplate.execute(status -> ClocktowerCaseFixture.persistPublished(entityManager, NOW));
        Long caseId = sample.mysteryCase().getId();

        // when & then
        transactionTemplate.executeWithoutResult(status -> {
            MysteryCase found = entityManager.find(MysteryCase.class, caseId);
            assertThat(found.getStatus()).isEqualTo(CaseStatus.PUBLISHED);
            assertThat(found.getDisplayTitle()).isEqualTo(ClocktowerCaseFixture.DISPLAY_TITLE);
            assertThat(found.getDifficulty()).isEqualTo(Difficulty.EASY);
            assertThat(found.getCulpritSuspect().getName()).isEqualTo("해리스");
            assertThat(found.getChatContextRevision()).isZero();

            List<Suspect> suspects = entityManager.createQuery(
                            "select s from Suspect s where s.mysteryCase.id = :caseId order by s.id", Suspect.class)
                    .setParameter("caseId", caseId)
                    .getResultList();
            assertThat(suspects)
                    .extracting(Suspect::getName, Suspect::isCulprit, Suspect::isCanConverse)
                    .containsExactly(
                            tuple("해리스", true, true),
                            tuple("마거릿", false, true),
                            tuple("톰", false, true),
                            tuple("엘리스", false, false));
            assertThat(suspects.get(3).getPersonaPrompt()).isNull();
            assertThat(suspects.get(3).getSpriteKey()).isNull();

            CaseMap caseMap = entityManager.createQuery(
                            "select m from CaseMap m where m.mysteryCase.id = :caseId", CaseMap.class)
                    .setParameter("caseId", caseId)
                    .getSingleResult();
            assertThat(caseMap.getMapUrl()).isEqualTo(ClocktowerCaseFixture.MAP_URL);

            List<MapHotspot> hotspots = entityManager.createQuery(
                            "select h from MapHotspot h where h.mysteryCase.id = :caseId", MapHotspot.class)
                    .setParameter("caseId", caseId)
                    .getResultList();
            assertThat(hotspots).extracting(MapHotspot::getType, MapHotspot::getRefId)
                    .containsExactlyInAnyOrder(
                            tuple(HotspotType.SUSPECT, sample.butler().getId()),
                            tuple(HotspotType.SUSPECT, sample.housekeeper().getId()),
                            tuple(HotspotType.SUSPECT, sample.gardener().getId()),
                            tuple(HotspotType.EVIDENCE, sample.smallBox().getId()),
                            tuple(HotspotType.EVIDENCE, sample.tornMemo().getId()));
        });
    }
}
