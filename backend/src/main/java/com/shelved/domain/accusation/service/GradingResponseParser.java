package com.shelved.domain.accusation.service;

import static com.shelved.domain.accusation.service.GradingResponseInvalidException.Reason.BLANK_EVIDENCE;
import static com.shelved.domain.accusation.service.GradingResponseInvalidException.Reason.BLANK_FEEDBACK;
import static com.shelved.domain.accusation.service.GradingResponseInvalidException.Reason.BLANK_REASON;
import static com.shelved.domain.accusation.service.GradingResponseInvalidException.Reason.DUPLICATE_ITEM;
import static com.shelved.domain.accusation.service.GradingResponseInvalidException.Reason.FEEDBACK_TOO_LONG;
import static com.shelved.domain.accusation.service.GradingResponseInvalidException.Reason.INVALID_RESULT;
import static com.shelved.domain.accusation.service.GradingResponseInvalidException.Reason.MISSING_FIELD;
import static com.shelved.domain.accusation.service.GradingResponseInvalidException.Reason.MISSING_ITEM;
import static com.shelved.domain.accusation.service.GradingResponseInvalidException.Reason.NOT_JSON;
import static com.shelved.domain.accusation.service.GradingResponseInvalidException.Reason.UNKNOWN_ITEM;

import com.shelved.common.entity.json.ElementResult;
import com.shelved.common.enums.RubricItemResult;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 채점 응답(구조화 출력 텍스트)의 형식 검증. 허용하는 형식은 이 한 곳에서 정한다 (GradingPromptBuilder.OUTPUT_SCHEMA)
 *
 * <ul>
 *     <li>JSON 객체 1개. items(배열)·feedback(문자열) 필수. 알 수 없는 필드는 무시한다</li>
 *     <li>항목마다 trickElementId(정수)·itemId·result·evidence·reason(문자열) 필수, result는 MET/MISSING/INCORRECT</li>
 *     <li>응답 항목 집합 = 기대 항목 집합이어야 한다. 누락·중복·모르는 항목은 모두 형식 오류. 순서는 상관없고 결과는 기대 순서로 돌려준다</li>
 *     <li>reason은 비면 안 된다. evidence는 MISSING일 때만 빈 문자열을 허용한다</li>
 *     <li>feedback은 공백뿐이면 안 되고, 앞뒤 공백을 뺀 길이가 상한 이하여야 한다</li>
 * </ul>
 * 형식 오류는 GradingResponseInvalidException(구분 코드만)이며 응답 본문은 예외 메시지에 넣지 않는다
 */
public final class GradingResponseParser {

    private static final String ITEMS_FIELD = "items";
    private static final String FEEDBACK_FIELD = "feedback";

    private final JsonMapper jsonMapper;

    public GradingResponseParser(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /**
     * @param text              모델 응답 텍스트
     * @param expectedKeyList   기대 항목 (GradingContext.expectedItemKeyList)
     * @param feedbackMaxLength 피드백 글자 수 상한
     */
    public ParsedGrading parse(String text, List<GradingContext.ItemKey> expectedKeyList, int feedbackMaxLength) {

        JsonNode root = readObject(text);

        JsonNode items = root.get(ITEMS_FIELD);
        JsonNode feedbackNode = root.get(FEEDBACK_FIELD);
        if (items == null || !items.isArray() || feedbackNode == null || !feedbackNode.isString()) {
            throw new GradingResponseInvalidException(MISSING_FIELD);
        }

        Set<GradingContext.ItemKey> expectedKeySet = Set.copyOf(expectedKeyList);
        Map<GradingContext.ItemKey, ElementResult> resultByKey = new HashMap<>();
        Set<GradingContext.ItemKey> seenKeySet = new HashSet<>();
        for (JsonNode item : items) {
            ElementResult elementResult = toElementResult(item);
            GradingContext.ItemKey key = new GradingContext.ItemKey(elementResult.trickElementId(), elementResult.itemId());
            if (!expectedKeySet.contains(key)) {
                throw new GradingResponseInvalidException(UNKNOWN_ITEM);
            }
            if (!seenKeySet.add(key)) {
                throw new GradingResponseInvalidException(DUPLICATE_ITEM);
            }
            resultByKey.put(key, elementResult);
        }
        if (!seenKeySet.equals(expectedKeySet)) {
            throw new GradingResponseInvalidException(MISSING_ITEM);
        }

        String feedback = feedbackNode.stringValue().strip();
        if (feedback.isEmpty()) {
            throw new GradingResponseInvalidException(BLANK_FEEDBACK);
        }
        if (feedback.length() > feedbackMaxLength) {
            throw new GradingResponseInvalidException(FEEDBACK_TOO_LONG);
        }

        List<ElementResult> elementResultList = expectedKeyList.stream()
                .map(resultByKey::get)
                .toList();

        return new ParsedGrading(elementResultList, feedback);
    }

    private JsonNode readObject(String text) {
        if (text == null || text.isBlank()) {
            throw new GradingResponseInvalidException(NOT_JSON);
        }
        JsonNode root;
        try {
            root = jsonMapper.readTree(text.strip());
        } catch (JacksonException e) {
            throw new GradingResponseInvalidException(NOT_JSON);
        }
        if (root == null || !root.isObject()) {
            throw new GradingResponseInvalidException(NOT_JSON);
        }
        return root;
    }

    private static ElementResult toElementResult(JsonNode item) {
        if (item == null || !item.isObject()) {
            throw new GradingResponseInvalidException(MISSING_FIELD);
        }
        JsonNode trickElementId = item.get("trickElementId");
        if (trickElementId == null || !trickElementId.isIntegralNumber() || !trickElementId.canConvertToLong()) {
            throw new GradingResponseInvalidException(MISSING_FIELD);
        }
        String itemId = requiredString(item, "itemId");
        String resultText = requiredString(item, "result");
        String evidence = requiredString(item, "evidence");
        String reason = requiredString(item, "reason");

        RubricItemResult result;
        try {
            result = RubricItemResult.valueOf(resultText);
        } catch (IllegalArgumentException e) {
            throw new GradingResponseInvalidException(INVALID_RESULT);
        }
        if (reason.isBlank()) {
            throw new GradingResponseInvalidException(BLANK_REASON);
        }
        if (evidence.isBlank() && result != RubricItemResult.MISSING) {
            throw new GradingResponseInvalidException(BLANK_EVIDENCE);
        }

        return new ElementResult(trickElementId.longValue(), itemId, result, evidence.strip(), reason.strip());
    }

    private static String requiredString(JsonNode item, String field) {
        JsonNode node = item.get(field);
        if (node == null || !node.isString()) {
            throw new GradingResponseInvalidException(MISSING_FIELD);
        }
        return node.stringValue();
    }

    /**
     * 검증을 통과한 응답. elementResultList는 기대 항목 순서
     */
    public record ParsedGrading(
            List<ElementResult> elementResultList,
            String feedback
    ) {

        public ParsedGrading {
            elementResultList = List.copyOf(elementResultList);
        }

        @Override
        public String toString() {
            return "ParsedGrading[itemCount=" + elementResultList.size() + "]";
        }
    }
}
