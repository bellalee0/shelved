package com.shelved.infra.llm;

import com.shelved.common.enums.LlmRequestType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 실제 API를 호출하지 않는 LLM 클라이언트 (shelved.llm.provider=fake, 기본값). 로컬 실행과 테스트에서 쓴다.
 * - 시나리오를 enqueue하면 호출마다 하나씩 꺼내 실행한다 (지연·대기·시간 초과·서비스 오류·형식 오류 재현)
 * - 시나리오가 없으면 요청 유형별 기본 문장을 돌려준다. 기본 문장에는 실제 사건 내용을 넣지 않는다
 * - 호출 횟수와 입력을 기록해 테스트에서 검사한다. 여러 스레드에서 동시에 호출해도 된다
 */
public class FakeLlmClient implements LlmClient {

    public static final String DEFAULT_TEXT = "(Fake 응답) 로컬 개발용 고정 문장입니다. 실제 AI는 호출하지 않았습니다.";
    public static final String MAIN_MODEL = "fake-main-model";
    public static final String FILTER_MODEL = "fake-filter-model";
    public static final int DEFAULT_INPUT_TOKENS = 100;
    public static final int DEFAULT_OUTPUT_TOKENS = 20;

    private final Queue<FakeLlmScenario> scenarioQueue = new ConcurrentLinkedQueue<>();
    private final Map<LlmRequestType, String> defaultTextMap = new ConcurrentHashMap<>();
    private final List<LlmRequest> requestList = new CopyOnWriteArrayList<>();

    @Override
    public LlmResponse call(LlmRequest request) {

        requestList.add(request);
        String model = modelOf(request.modelTier());

        FakeLlmScenario scenario = scenarioQueue.poll();
        if (scenario == null) {
            return new LlmResponse(defaultTextOf(request.requestType()), DEFAULT_INPUT_TOKENS, DEFAULT_OUTPUT_TOKENS, model);
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
     * 다음 호출들에 쓸 시나리오를 순서대로 추가한다
     */
    public void enqueue(FakeLlmScenario... scenarios) {
        scenarioQueue.addAll(List.of(scenarios));
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
        requestList.clear();
        defaultTextMap.clear();
    }

    private String defaultTextOf(LlmRequestType requestType) {
        return defaultTextMap.getOrDefault(requestType, DEFAULT_TEXT);
    }
}
