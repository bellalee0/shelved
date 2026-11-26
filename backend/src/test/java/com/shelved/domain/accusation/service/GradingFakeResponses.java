package com.shelved.domain.accusation.service;

import com.shelved.common.enums.RubricItemResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

/**
 * 채점 응답 JSON을 만드는 테스트 도우미. FakeLlmScenario.respond(...)에 넘겨 쓴다.
 * 지연·시간 초과·서비스 오류는 FakeLlmScenario의 기존 팩토리를 조합한다 (예: delayed(d, respond(allMet(keys))))
 */
final class GradingFakeResponses {

    static final String FEEDBACK = "녹음기를 짚은 것은 맞아요. 어디에, 어떤 목적으로 사용했을까요?";
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private GradingFakeResponses() {
    }

    static String allMet(List<GradingContext.ItemKey> keys) {
        return of(keys, keys.stream().map(key -> RubricItemResult.MET).toList());
    }

    static String allMissing(List<GradingContext.ItemKey> keys) {
        return of(keys, keys.stream().map(key -> RubricItemResult.MISSING).toList());
    }

    /**
     * 첫 항목만 MET, 나머지 MISSING
     */
    static String firstMet(List<GradingContext.ItemKey> keys) {
        List<RubricItemResult> results = new ArrayList<>();
        for (int i = 0; i < keys.size(); i++) {
            results.add(i == 0 ? RubricItemResult.MET : RubricItemResult.MISSING);
        }
        return of(keys, results);
    }

    /**
     * MET 없이 INCORRECT·MISSING만 섞음
     */
    static String incorrectMixed(List<GradingContext.ItemKey> keys) {
        List<RubricItemResult> results = new ArrayList<>();
        for (int i = 0; i < keys.size(); i++) {
            results.add(i % 2 == 0 ? RubricItemResult.INCORRECT : RubricItemResult.MISSING);
        }
        return of(keys, results);
    }

    static String missingItem(List<GradingContext.ItemKey> keys) {
        return of(keys.subList(0, keys.size() - 1), keys.subList(0, keys.size() - 1).stream()
                .map(key -> RubricItemResult.MET).toList());
    }

    static String unknownItem(List<GradingContext.ItemKey> keys) {
        List<GradingContext.ItemKey> withUnknown = new ArrayList<>(keys);
        withUnknown.add(new GradingContext.ItemKey(keys.getFirst().trickElementId(), "trk-9-i9"));
        return of(withUnknown, withUnknown.stream().map(key -> RubricItemResult.MET).toList());
    }

    static String duplicateItem(List<GradingContext.ItemKey> keys) {
        List<GradingContext.ItemKey> withDuplicate = new ArrayList<>(keys);
        withDuplicate.add(keys.getFirst());
        return of(withDuplicate, withDuplicate.stream().map(key -> RubricItemResult.MET).toList());
    }

    static String invalidResult(List<GradingContext.ItemKey> keys) {
        return allMet(keys).replaceFirst("\"MET\"", "\"PARTIAL\"");
    }

    static String blankFeedback(List<GradingContext.ItemKey> keys) {
        return json(itemList(keys, keys.stream().map(key -> RubricItemResult.MET).toList()), "   ");
    }

    static String notJson() {
        return "채점 결과: 전부 맞았습니다.";
    }

    static String of(List<GradingContext.ItemKey> keys, List<RubricItemResult> results) {
        return json(itemList(keys, results), FEEDBACK);
    }

    static String json(List<Map<String, Object>> items, String feedback) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("items", items);
        root.put("feedback", feedback);
        return JSON_MAPPER.writeValueAsString(root);
    }

    static List<Map<String, Object>> itemList(List<GradingContext.ItemKey> keys, List<RubricItemResult> results) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < keys.size(); i++) {
            RubricItemResult result = results.get(i);
            items.add(item(keys.get(i), result.name(), result == RubricItemResult.MISSING ? "" : "녹음기를 사용했다", "판단 이유"));
        }
        return items;
    }

    static Map<String, Object> item(GradingContext.ItemKey key, String result, String evidence, String reason) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("trickElementId", key.trickElementId());
        item.put("itemId", key.itemId());
        item.put("result", result);
        item.put("evidence", evidence);
        item.put("reason", reason);
        return item;
    }
}
