package com.shelved.domain.accusation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.shelved.common.entity.CaseTrickElement;
import com.shelved.common.entity.MysteryCase;
import com.shelved.common.entity.Room;
import com.shelved.common.entity.Suspect;
import com.shelved.common.entity.json.GradingRubric;
import com.shelved.common.entity.json.RubricItem;
import com.shelved.common.enums.Difficulty;
import com.shelved.common.exception.CustomException;
import com.shelved.common.exception.ErrorCode;
import com.shelved.domain.airequest.AiRequestIntegrationTestSupport;
import com.shelved.fixture.ClocktowerCaseFixture;
import com.shelved.infra.llm.FakeLlmScenario;
import com.shelved.infra.llm.LlmCallContext;
import com.shelved.infra.llm.LlmRequest;
import com.shelved.infra.llm.LlmResponse;
import com.shelved.infra.llm.LlmUsageRecordingClient;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.function.Function;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * 채점 컨텍스트 로더. 가상 사건 「시계탑 저택의 마지막 종소리」에 테스트용 두 번째 요소(가상 기준)를 더해 쓴다.
 * 커밋된 시드(clocktower-sample.json)는 바꾸지 않는다 (시드 로더 테스트가 요소 1개를 전제로 하고 W5가 같은 파일을 고친다)
 */
@ExtendWith(OutputCaptureExtension.class)
class GradingContextLoaderIntegrationTest extends AiRequestIntegrationTestSupport {

    private static final String ANSWER = "집사가 서재에 녹음기를 숨겨 두고 주인의 목소리를 틀었다.";
    private static final String ELEMENT_2_DESCRIPTION = "범행 뒤 녹음기를 회수해 흔적을 없애려 했다";
    private static final String ELEMENT_2_GUIDE_QUESTION = "사건 뒤에 서재를 드나든 사람은 누구였나요?";
    private static final List<RubricItem> ELEMENT_2_ITEMS = List.of(
            new RubricItem("trk-2-i1", "회수 시점", "범행 뒤에 장치를 회수했음을 밝힘", List.of("나중에 녹음기를 치웠다"), List.of()),
            new RubricItem("trk-2-i2", "회수 목적", "흔적을 없애기 위함을 밝힘", List.of("증거를 없애려고"), List.of("고장 나서")));

    @Autowired
    private GradingContextLoader gradingContextLoader;

    @Autowired
    private AccusationGrader accusationGrader;

    @Autowired
    private LlmUsageRecordingClient llmUsageRecordingClient;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Long roomId;
    private Long element2Id;

    @BeforeEach
    void setUpGradingCase() {
        element2Id = addElement(2, ELEMENT_2_DESCRIPTION, ELEMENT_2_ITEMS, new GradingRubric.EasyFeedback(
                ELEMENT_2_GUIDE_QUESTION, List.of(sample.tornMemo().getId()), List.of(sample.housekeeper().getId())));
        roomId = saveReadyRoom(user);
    }

    @Test
    void load_쉬움_요소항목순서와쉬움참고이름변환_범인일치() {

        // when
        GradingContext context = load(sample.butler().getId());

        // then
        assertThat(context.difficulty()).isEqualTo(Difficulty.EASY);
        assertThat(context.suspectName()).isEqualTo("해리스");
        assertThat(context.culpritCorrect()).isTrue();
        assertThat(context.trickExplanation()).isEqualTo(ANSWER);
        assertThat(context.glossary()).isEqualTo(ClocktowerCaseFixture.GLOSSARY);
        assertThat(context.elementList())
                .extracting(GradingContext.Element::trickElementId, GradingContext.Element::description)
                .containsExactly(
                        tuple(sample.trickElement().getId(), ClocktowerCaseFixture.TRICK_ELEMENT_DESCRIPTION),
                        tuple(element2Id, ELEMENT_2_DESCRIPTION));
        assertThat(context.expectedItemKeyList()).extracting(GradingContext.ItemKey::itemId)
                .containsExactly("trk-1-i1", "trk-1-i2", "trk-2-i1", "trk-2-i2");
        GradingContext.Item item = context.elementList().getFirst().itemList().get(1);
        assertThat(item.criterion()).isEqualTo("피해자가 살아 있는 시각을 늦춰 보이게 함");
        assertThat(item.acceptExampleList()).containsExactly("사망 시각을 속였다");
        assertThat(item.rejectExampleList()).containsExactly("비명을 가렸다");
        assertThat(context.elementList())
                .extracting(GradingContext.Element::easyGuide)
                .containsExactly(
                        new GradingContext.EasyGuide(ClocktowerCaseFixture.EASY_GUIDE_QUESTION,
                                List.of("서재의 작은 상자"), List.of("해리스")),
                        new GradingContext.EasyGuide(ELEMENT_2_GUIDE_QUESTION, List.of("찢긴 메모"), List.of("마거릿")));
    }

    @Test
    void load_범인아닌인물지목_범인불일치() {

        // when
        GradingContext context = load(sample.gardener().getId());

        // then
        assertThat(context.culpritCorrect()).isFalse();
        assertThat(context.suspectName()).isEqualTo("톰");
    }

    @Test
    void load_보통_쉬움참고없음() {

        // given
        changeDifficulty(Difficulty.NORMAL);

        // when
        GradingContext context = load(sample.butler().getId());

        // then
        assertThat(context.difficulty()).isEqualTo(Difficulty.NORMAL);
        assertThat(context.elementList()).extracting(GradingContext.Element::easyGuide).containsOnlyNulls();
    }

    @Test
    void load_쿼리수_요소수와무관하게사건단위조회() {

        // given
        long twoElements = countStatements(room -> gradingContextLoader.load(room, sample.butler().getId(), ANSWER));
        addElement(3, "가상 요소 3", List.of(
                new RubricItem("trk-3-i1", "가상 항목", "가상 기준", List.of(), List.of()),
                new RubricItem("trk-3-i2", "가상 항목 2", "가상 기준 2", List.of(), List.of())),
                new GradingRubric.EasyFeedback("가상 질문", List.of(sample.smallBox().getId()), List.of(sample.gardener().getId())));

        // when
        long threeElements = countStatements(room -> gradingContextLoader.load(room, sample.butler().getId(), ANSWER));

        // then 사건 1 + 인물 목록 1 + 요소 목록 1 + 물증 목록 1(쉬움)
        assertThat(twoElements).isEqualTo(4);
        assertThat(threeElements).isEqualTo(twoElements);
    }

    @Test
    void load_다른사건의인물_SUSPECT_NOT_FOUND() {

        // given
        Long otherSuspectId = transactionTemplate.execute(status -> {
            MysteryCase other = new MysteryCase(T0);
            other.updateCardInfo("다른 가상 사건", Difficulty.EASY, List.of("가상"));
            entityManager.persist(other);
            Suspect stranger = new Suspect(other, "낯선 사람", null, null, null, false, false);
            entityManager.persist(stranger);
            return stranger.getId();
        });

        // when & then
        assertThatThrownBy(() -> load(otherSuspectId))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SUSPECT_NOT_FOUND));
    }

    @Test
    void load_핵심요소0개_채점불가예외() {

        // given 요소가 없는 사건과 세션
        Long emptyRoomId = transactionTemplate.execute(status -> {
            MysteryCase emptyCase = new MysteryCase(T0);
            emptyCase.updateCardInfo("요소 없는 가상 사건", Difficulty.HARD, List.of("가상"));
            entityManager.persist(emptyCase);
            Suspect suspect = new Suspect(emptyCase, "가상 인물", null, null, null, true, false);
            entityManager.persist(suspect);
            emptyCase.updateSolution(suspect, "가상 요약", "가상 해설");
            Room room = new Room(emptyCase, entityManager.getReference(com.shelved.common.entity.User.class, user.getId()),
                    false, LIMIT_SEC, T0);
            entityManager.persist(room);
            return room.getId();
        });
        Long suspectId = transactionTemplate.execute(status -> entityManager.createQuery(
                        "select s.id from Suspect s where s.mysteryCase.id = (select r.mysteryCase.id from Room r where r.id = :roomId)",
                        Long.class)
                .setParameter("roomId", emptyRoomId)
                .getSingleResult());

        // when & then
        assertThatThrownBy(() -> transactionTemplate.execute(status ->
                gradingContextLoader.load(entityManager.find(Room.class, emptyRoomId), suspectId, ANSWER)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("핵심 요소가 없는");
    }

    @Test
    void load_항목0개요소_전부충족으로보지않고예외() {

        // given
        addElement(3, "항목이 빠진 가상 요소", List.of(), null);

        // when & then
        assertThatThrownBy(() -> load(sample.butler().getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("필수 판정 항목이 없는");
    }

    @Test
    void load_쉬움참고에사건에없는물증ID_시드오류예외() {

        // given
        addElement(3, "가상 요소 3", List.of(new RubricItem("trk-3-i1", "가상 항목", "가상 기준", List.of(), List.of())),
                new GradingRubric.EasyFeedback("가상 질문", List.of(999_999L), List.of()));

        // when & then
        assertThatThrownBy(() -> load(sample.butler().getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("물증");
    }

    @Test
    void grade_Fake형식오류호출_로그에답안기준응답본문없음(CapturedOutput output) {

        // given
        GradingContext context = load(sample.butler().getId());
        LlmRequest request = accusationGrader.buildLlmRequest(context);
        String invalidResponse = GradingFakeResponses.missingItem(context.expectedItemKeyList());
        fakeLlmClient.enqueue(FakeLlmScenario.respond(invalidResponse));

        // when
        LlmResponse response = llmUsageRecordingClient.call(request, LlmCallContext.game(roomId, sample.mysteryCase().getId()));
        assertThatThrownBy(() -> accusationGrader.grade(context, response.text()))
                .isInstanceOf(GradingResponseInvalidException.class);

        // then
        assertThat(fakeLlmClient.lastRequest().outputSchema()).isEqualTo(GradingPromptBuilder.OUTPUT_SCHEMA);
        assertThat(output.getAll())
                .doesNotContain(ANSWER, "녹음 장치를 사용했음을 밝힘", ELEMENT_2_DESCRIPTION, ELEMENT_2_GUIDE_QUESTION)
                .doesNotContain(GradingFakeResponses.FEEDBACK, "판단 이유", "submitted_answer");
    }

    private GradingContext load(Long suspectId) {
        return transactionTemplate.execute(status ->
                gradingContextLoader.load(entityManager.find(Room.class, roomId), suspectId, ANSWER));
    }

    private Long addElement(int orderIndex, String description, List<RubricItem> items, GradingRubric.EasyFeedback easyFeedback) {
        return transactionTemplate.execute(status -> {
            CaseTrickElement element = new CaseTrickElement(
                    entityManager.getReference(MysteryCase.class, sample.mysteryCase().getId()),
                    description, new GradingRubric(items, easyFeedback), orderIndex);
            entityManager.persist(element);
            return element.getId();
        });
    }

    private void changeDifficulty(Difficulty difficulty) {
        transactionTemplate.executeWithoutResult(status -> entityManager.find(MysteryCase.class, sample.mysteryCase().getId())
                .updateCardInfo(ClocktowerCaseFixture.DISPLAY_TITLE, difficulty, ClocktowerCaseFixture.KEYWORD_TAGS));
    }

    // 세션 행 조회 뒤 로더가 실행한 SQL 문 수
    private long countStatements(Function<Room, GradingContext> loader) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        return transactionTemplate.execute(status -> {
            Room room = entityManager.find(Room.class, roomId);
            boolean enabled = statistics.isStatisticsEnabled();
            statistics.setStatisticsEnabled(true);
            statistics.clear();
            try {
                loader.apply(room);
                return statistics.getPrepareStatementCount();
            } finally {
                statistics.setStatisticsEnabled(enabled);
            }
        });
    }
}
