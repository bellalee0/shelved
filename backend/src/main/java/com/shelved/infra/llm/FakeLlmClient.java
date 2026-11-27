package com.shelved.infra.llm;

import com.shelved.common.enums.LlmRequestType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.json.JsonMapper;

/**
 * 실제 API를 호출하지 않는 LLM 클라이언트 (shelved.llm.provider=fake, 기본값). 로컬 실행과 테스트에서 쓴다.
 * - 시나리오를 enqueue하면 호출마다 하나씩 꺼내 실행한다 (지연·대기·시간 초과·서비스 오류·형식 오류 재현).
 *   본 모델(MAIN) 호출은 enqueue, 분류 모델(FILTER) 호출은 enqueueFilter의 시나리오를 따로 꺼낸다.
 *   한 요청이 분류 → 본 응답 순으로 두 번 호출해도, 동시에 여러 요청이 와도 시나리오가 섞이지 않게 하기 위함이다
 * - 시나리오가 없으면 요청 유형별 기본 문장을 돌려준다. 기본 문장에는 실제 사건 내용을 넣지 않는다
 * - 질문 분류(QUESTION_FILTER)의 기본 응답은 허용 JSON이다. 로컬 프론트엔드 연동 확인용으로 질문에 "범인이 누구"가 들어 있을 때만
 *   차단 JSON을 돌려준다. Fake 전용 규칙이며 실제 분류(작은 모델의 의도 분류)를 대신하지 않는다
 * - 구조화 출력 스키마가 있는 채점(GRADING) 요청의 기본 응답은, 시스템 프롬프트의 채점 기준 블록에서 읽은 모든 항목을 MET로 둔 JSON이다
 *   (범인을 맞히면 정답, 틀리면 오답이 된다). 로컬에서 비용 없이 정답·3회 실패 흐름을 확인하기 위함이며 사건 내용은 담지 않는다.
 *   읽는 형식은 GradingPromptBuilder의 출력과 묶여 있으며 테스트(FakeGradingResponseTest)로 고정한다
 * - 호출 횟수와 입력을 기록해 테스트에서 검사한다. 여러 스레드에서 동시에 호출해도 된다
 */
public class FakeLlmClient implements LlmClient {

    public static final String DEFAULT_TEXT = "(Fake 응답) 로컬 개발용 고정 문장입니다. 실제 AI는 호출하지 않았습니다.";
    public static final String MAIN_MODEL = "fake-main-model";
    public static final String FILTER_MODEL = "fake-filter-model";
    public static final int DEFAULT_INPUT_TOKENS = 100;
    public static final int DEFAULT_OUTPUT_TOKENS = 20;
    public static final String FILTER_ALLOW_TEXT = "{\"blocked\": false}";
    public static final String FILTER_BLOCK_TEXT = "{\"blocked\": true}";
    // Fake 전용 차단 규칙 (로컬 확인용). 실제 분류기는 금칙어로 판정하지 않는다
    public static final String FAKE_BLOCKED_PHRASE = "범인이 누구";
    // 채점 기본 응답 (모든 항목 MET)
    public static final String FAKE_GRADING_FEEDBACK = "(Fake 채점) 로컬 개발용 고정 피드백입니다. 실제 AI는 호출하지 않았습니다.";
    static final String FAKE_GRADING_EVIDENCE = "(Fake 채점) 답안 전체";
    static final String FAKE_GRADING_REASON = "(Fake 채점) 기본 응답은 모든 항목을 충족으로 판정합니다.";
    // GradingPromptBuilder의 채점 기준 블록: "### 요소 n (trickElementId: 101)" 다음 줄들의 "- itemId: trk-1-i1 / 항목: ..."
    private static final Pattern GRADING_ELEMENT_PATTERN = Pattern.compile("^### 요소 \\d+ \\(trickElementId: (\\d+)\\)\\s*$");
    private static final Pattern GRADING_ITEM_PATTERN = Pattern.compile("^- itemId: (\\S+) / 항목: .*$");
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final Queue<FakeLlmScenario> scenarioQueue = new ConcurrentLinkedQueue<>();
    private final Queue<FakeLlmScenario> filterScenarioQueue = new ConcurrentLinkedQueue<>();
    private final Map<LlmRequestType, String> defaultTextMap = new ConcurrentHashMap<>();
    private final List<LlmRequest> requestList = new CopyOnWriteArrayList<>();

    @Override
    public LlmResponse call(LlmRequest request) {

        requestList.add(request);
        String model = modelOf(request.modelTier());

        FakeLlmScenario scenario = request.modelTier() == LlmModelTier.FILTER
                ? filterScenarioQueue.poll()
                : scenarioQueue.poll();
        if (scenario == null) {
            return new LlmResponse(defaultTextOf(request), DEFAULT_INPUT_TOKENS, DEFAULT_OUTPUT_TOKENS, model);
        }
        return scenario.run(request, model);
    }

    @Override
    public String modelOf(LlmModelTier modelTier) {
        return switch (modelTier) {
            case MAIN -> MAIN_MODEL;
            case FILTER -> FILTER_MODEL;
        };
    }

    /**
     * 다음 본 모델(MAIN) 호출들에 쓸 시나리오를 순서대로 추가한다
     */
    public void enqueue(FakeLlmScenario... scenarios) {
        scenarioQueue.addAll(List.of(scenarios));
    }

    /**
     * 다음 분류 모델(FILTER) 호출들에 쓸 시나리오를 순서대로 추가한다
     */
    public void enqueueFilter(FakeLlmScenario... scenarios) {
        filterScenarioQueue.addAll(List.of(scenarios));
    }

    /**
     * 시나리오가 없을 때 이 요청 유형에 돌려줄 기본 문장 (도메인이 기대하는 응답 형식을 로컬에서 맞출 때 쓴다)
     */
    public void setDefaultText(LlmRequestType requestType, String text) {
        defaultTextMap.put(requestType, text);
    }

    public int callCount() {
        return requestList.size();
    }

    public int callCount(LlmModelTier modelTier) {
        return (int) requestList.stream()
                .filter(request -> request.modelTier() == modelTier)
                .count();
    }

    public List<LlmRequest> requestList() {
        return new ArrayList<>(requestList);
    }

    /**
     * 마지막 호출의 입력. 호출이 없으면 null
     */
    public LlmRequest lastRequest() {
        List<LlmRequest> snapshot = requestList();
        return snapshot.isEmpty() ? null : snapshot.getLast();
    }

    /**
     * 남은 시나리오·기록·기본 문장을 모두 지운다 (테스트 사이 초기화)
     */
    public void reset() {
        scenarioQueue.clear();
        filterScenarioQueue.clear();
        requestList.clear();
        defaultTextMap.clear();
    }

    private String defaultTextOf(LlmRequest request) {
        String text = defaultTextMap.get(request.requestType());
        if (text != null) {
            return text;
        }
        if (request.requestType() == LlmRequestType.QUESTION_FILTER) {
            boolean blocked = request.messageList().stream()
                    .anyMatch(message -> message.content() != null && message.content().contains(FAKE_BLOCKED_PHRASE));
            return blocked ? FILTER_BLOCK_TEXT : FILTER_ALLOW_TEXT;
        }
        if (request.requestType() == LlmRequestType.GRADING && request.outputSchema() != null) {
            return allMetGradingText(request.systemPrompt());
        }
        return DEFAULT_TEXT;
    }

    // 채점 기준 블록의 모든 (trickElementId, itemId)를 MET로 둔 채점 응답 JSON
    static String allMetGradingText(String systemPrompt) {
        List<Map<String, Object>> itemList = new ArrayList<>();
        Long trickElementId = null;
        for (String line : systemPrompt == null ? new String[0] : systemPrompt.split("\\R")) {
            Matcher elementMatcher = GRADING_ELEMENT_PATTERN.matcher(line);
            if (elementMatcher.matches()) {
                trickElementId = Long.parseLong(elementMatcher.group(1));
                continue;
            }
            Matcher itemMatcher = GRADING_ITEM_PATTERN.matcher(line);
            if (itemMatcher.matches() && trickElementId != null) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("trickElementId", trickElementId);
                item.put("itemId", itemMatcher.group(1));
                item.put("result", "MET");
                item.put("evidence", FAKE_GRADING_EVIDENCE);
                item.put("reason", FAKE_GRADING_REASON);
                itemList.add(item);
            }
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("items", itemList);
        root.put("feedback", FAKE_GRADING_FEEDBACK);
        return JSON_MAPPER.writeValueAsString(root);
    }
}
