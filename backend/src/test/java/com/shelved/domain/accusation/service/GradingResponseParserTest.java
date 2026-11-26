package com.shelved.domain.accusation.service;

import static com.shelved.domain.accusation.service.GradingTestData.ELEMENT_1_ID;
import static com.shelved.domain.accusation.service.GradingTestData.EXPECTED_KEYS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.shelved.common.entity.json.ElementResult;
import com.shelved.common.enums.RubricItemResult;
import com.shelved.domain.accusation.service.GradingResponseInvalidException.Reason;
import com.shelved.domain.airequest.service.AiResponseInvalidException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class GradingResponseParserTest {

    private static final int FEEDBACK_MAX_LENGTH = 100;

    private final GradingResponseParser parser = new GradingResponseParser(JsonMapper.builder().build());

    @Test
    void parse_정상_기대순서의항목결과와피드백() {

        // given
        String text = GradingFakeResponses.firstMet(EXPECTED_KEYS);

        // when
        GradingResponseParser.ParsedGrading parsed = parser.parse(text, EXPECTED_KEYS, FEEDBACK_MAX_LENGTH);

        // then
        assertThat(parsed.elementResultList())
                .extracting(ElementResult::trickElementId, ElementResult::itemId, ElementResult::result)
                .containsExactly(
                        tuple(ELEMENT_1_ID, "trk-1-i1", RubricItemResult.MET),
                        tuple(ELEMENT_1_ID, "trk-1-i2", RubricItemResult.MISSING),
                        tuple(GradingTestData.ELEMENT_2_ID, "trk-2-i1", RubricItemResult.MISSING),
                        tuple(GradingTestData.ELEMENT_2_ID, "trk-2-i2", RubricItemResult.MISSING));
        assertThat(parsed.elementResultList().getFirst().evidence()).isEqualTo("녹음기를 사용했다");
        assertThat(parsed.elementResultList().get(1).evidence()).isEmpty();
        assertThat(parsed.feedback()).isEqualTo(GradingFakeResponses.FEEDBACK);
    }

    @Test
    void parse_응답항목순서가달라도_통과하고기대순서로정렬() {

        // given
        List<GradingContext.ItemKey> reversed = new ArrayList<>(EXPECTED_KEYS).reversed();
        String text = GradingFakeResponses.allMet(reversed);

        // when
        GradingResponseParser.ParsedGrading parsed = parser.parse(text, EXPECTED_KEYS, FEEDBACK_MAX_LENGTH);

        // then
        assertThat(parsed.elementResultList()).extracting(ElementResult::itemId)
                .containsExactly("trk-1-i1", "trk-1-i2", "trk-2-i1", "trk-2-i2");
    }

    @Test
    void parse_JSON아님_NOT_JSON() {
        assertReason(GradingFakeResponses.notJson(), Reason.NOT_JSON);
        assertReason("", Reason.NOT_JSON);
        assertReason("[1, 2]", Reason.NOT_JSON);
    }

    @Test
    void parse_필수필드누락또는타입오류_MISSING_FIELD() {
        assertReason("{\"items\": []}", Reason.MISSING_FIELD);
        assertReason("{\"feedback\": \"좋아요\"}", Reason.MISSING_FIELD);
        assertReason("{\"items\": {}, \"feedback\": \"좋아요\"}", Reason.MISSING_FIELD);
        assertReason(GradingFakeResponses.json(List.of(Map.of(
                "trickElementId", ELEMENT_1_ID, "itemId", "trk-1-i1", "result", "MET", "evidence", "녹음기")), "좋아요"),
                Reason.MISSING_FIELD);
        assertReason(GradingFakeResponses.json(List.of(Map.of(
                "trickElementId", "101", "itemId", "trk-1-i1", "result", "MET", "evidence", "녹음기", "reason", "이유")), "좋아요"),
                Reason.MISSING_FIELD);
    }

    @Test
    void parse_result가enum밖_INVALID_RESULT() {
        assertReason(GradingFakeResponses.invalidResult(EXPECTED_KEYS), Reason.INVALID_RESULT);
    }

    @Test
    void parse_항목누락_MISSING_ITEM() {
        assertReason(GradingFakeResponses.missingItem(EXPECTED_KEYS), Reason.MISSING_ITEM);
    }

    @Test
    void parse_모르는항목_UNKNOWN_ITEM() {
        assertReason(GradingFakeResponses.unknownItem(EXPECTED_KEYS), Reason.UNKNOWN_ITEM);
    }

    @Test
    void parse_요소ID가다른같은itemId_UNKNOWN_ITEM() {

        // given 항목 ID는 맞지만 다른 요소의 것으로 답함
        List<GradingContext.ItemKey> keys = new ArrayList<>(EXPECTED_KEYS);
        keys.set(0, new GradingContext.ItemKey(GradingTestData.ELEMENT_2_ID, "trk-1-i1"));

        // when & then
        assertReason(GradingFakeResponses.allMet(keys), Reason.UNKNOWN_ITEM);
    }

    @Test
    void parse_중복항목_DUPLICATE_ITEM() {
        assertReason(GradingFakeResponses.duplicateItem(EXPECTED_KEYS), Reason.DUPLICATE_ITEM);
    }

    @Test
    void parse_reason빈값_BLANK_REASON() {

        // given
        List<Map<String, Object>> items = GradingFakeResponses.itemList(EXPECTED_KEYS,
                EXPECTED_KEYS.stream().map(key -> RubricItemResult.MISSING).toList());
        items.set(0, GradingFakeResponses.item(EXPECTED_KEYS.getFirst(), "MISSING", "", " "));

        // when & then
        assertReason(GradingFakeResponses.json(items, "좋아요"), Reason.BLANK_REASON);
    }

    @Test
    void parse_evidence빈값_MISSING이면허용_MET이면BLANK_EVIDENCE() {

        // given
        List<Map<String, Object>> items = GradingFakeResponses.itemList(EXPECTED_KEYS,
                EXPECTED_KEYS.stream().map(key -> RubricItemResult.MISSING).toList());
        items.set(0, GradingFakeResponses.item(EXPECTED_KEYS.getFirst(), "MET", "", "도구를 밝힘"));

        // when & then
        assertThat(parser.parse(GradingFakeResponses.allMissing(EXPECTED_KEYS), EXPECTED_KEYS, FEEDBACK_MAX_LENGTH)
                .elementResultList()).allSatisfy(result -> assertThat(result.evidence()).isEmpty());
        assertReason(GradingFakeResponses.json(items, "좋아요"), Reason.BLANK_EVIDENCE);
    }

    @Test
    void parse_피드백공백뿐_BLANK_FEEDBACK() {
        assertReason(GradingFakeResponses.blankFeedback(EXPECTED_KEYS), Reason.BLANK_FEEDBACK);
    }

    @Test
    void parse_피드백상한초과_FEEDBACK_TOO_LONG_상한까지는통과() {

        // given
        List<Map<String, Object>> items = GradingFakeResponses.itemList(EXPECTED_KEYS,
                EXPECTED_KEYS.stream().map(key -> RubricItemResult.MET).toList());
        String atLimit = GradingFakeResponses.json(items, "  " + "가".repeat(FEEDBACK_MAX_LENGTH) + "  ");
        String overLimit = GradingFakeResponses.json(items, "가".repeat(FEEDBACK_MAX_LENGTH + 1));

        // when & then
        assertThat(parser.parse(atLimit, EXPECTED_KEYS, FEEDBACK_MAX_LENGTH).feedback()).hasSize(FEEDBACK_MAX_LENGTH);
        assertReason(overLimit, Reason.FEEDBACK_TOO_LONG);
    }

    @Test
    void parse_형식오류메시지_구분코드만_응답본문없음() {

        // given
        String text = GradingFakeResponses.missingItem(EXPECTED_KEYS);

        // when & then
        assertThatThrownBy(() -> parser.parse(text, EXPECTED_KEYS, FEEDBACK_MAX_LENGTH))
                .isInstanceOf(AiResponseInvalidException.class)
                .hasMessage("GRADING_MISSING_ITEM")
                .hasNoCause();
    }

    private void assertReason(String text, Reason reason) {
        assertThatThrownBy(() -> parser.parse(text, EXPECTED_KEYS, FEEDBACK_MAX_LENGTH))
                .isInstanceOfSatisfying(GradingResponseInvalidException.class,
                        e -> assertThat(e.getReason()).isEqualTo(reason));
    }
}
