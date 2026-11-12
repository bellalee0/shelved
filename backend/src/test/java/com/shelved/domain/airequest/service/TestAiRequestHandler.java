package com.shelved.domain.airequest.service;

import com.shelved.common.entity.AiRequest;
import com.shelved.common.entity.Room;
import com.shelved.common.enums.AiRequestStatus;
import com.shelved.common.enums.AiRequestType;
import com.shelved.common.enums.LlmRequestType;
import com.shelved.common.exception.CustomException;
import com.shelved.common.exception.ErrorCode;
import com.shelved.domain.room.repository.AiRequestRepository;
import com.shelved.infra.llm.LlmMessage;
import com.shelved.infra.llm.LlmModelTier;
import com.shelved.infra.llm.LlmRequest;
import com.shelved.infra.llm.LlmResponse;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 테스트 전용 도메인 콜백. 테이블을 만들지 않고 저장을 메모리 목록으로 흉내 낸다.
 * - 한도: 같은 유형의 SUCCEEDED + PROCESSING 수가 limit 이상이면 HINT_LIMIT_EXCEEDED
 * - 형식: 응답이 "답변:"으로 시작하지 않으면 AiResponseInvalidException
 * - 성공 응답: 답변과 당시 사용 횟수·세션 상태 (스냅샷 검증용)
 */
public class TestAiRequestHandler implements AiRequestHandler<TestAiRequestHandler.Context> {

    public static final String ANSWER_PREFIX = "답변:";
    public static final String SYSTEM_PROMPT = "가상 사건 「시계탑 저택의 마지막 종소리」의 인물로 답한다.";

    private final AiRequestType type;
    private final AiRequestRepository aiRequestRepository;
    private final int limit;

    public final AtomicInteger prepareCount = new AtomicInteger();
    public final AtomicInteger completeCount = new AtomicInteger();
    // 게임 데이터 저장 흉내 (저장된 답변)
    public final List<String> savedAnswerList = new CopyOnWriteArrayList<>();

    public TestAiRequestHandler(AiRequestType type, AiRequestRepository aiRequestRepository, int limit) {
        this.type = type;
        this.aiRequestRepository = aiRequestRepository;
        this.limit = limit;
    }

    @Override
    public AiRequestType type() {
        return type;
    }

    @Override
    public Context prepare(Room room, AiRequestCommand command) {
        long used = countOf(room, AiRequestStatus.SUCCEEDED) + countOf(room, AiRequestStatus.PROCESSING);
        if (used >= limit) {
            throw new CustomException(ErrorCode.HINT_LIMIT_EXCEEDED);
        }
        prepareCount.incrementAndGet();
        return new Context(command.input().question());
    }

    @Override
    public LlmRequest buildLlmRequest(Context context) {
        return new LlmRequest(llmRequestTypeOf(type), LlmModelTier.MAIN, SYSTEM_PROMPT,
                List.of(LlmMessage.user(context.question())), 200);
    }

    @Override
    public AiRequestSuccess complete(Room room, AiRequest aiRequest, Context context, LlmResponse response) {
        if (!response.text().startsWith(ANSWER_PREFIX)) {
            throw new AiResponseInvalidException("답변 형식이 아님");
        }
        completeCount.incrementAndGet();
        String answer = response.text().substring(ANSWER_PREFIX.length()).trim();
        savedAnswerList.add(answer);
        // 이번 요청은 아직 PROCESSING이므로 성공 수 + 1이 이번 요청까지의 사용 횟수
        long usedCount = countOf(room, AiRequestStatus.SUCCEEDED) + 1;

        return new AiRequestSuccess(null, new TestResponse(answer, usedCount, room.getStatus().name()));
    }

    private long countOf(Room room, AiRequestStatus status) {
        return aiRequestRepository.countByRoomIdAndRequestTypeAndStatus(room.getId(), type, status);
    }

    private static LlmRequestType llmRequestTypeOf(AiRequestType type) {
        return switch (type) {
            case NPC_ASK -> LlmRequestType.NPC_CHAT;
            case HINT -> LlmRequestType.HINT;
            case HINT_FOLLOWUP -> LlmRequestType.HINT_FOLLOWUP;
            case ACCUSATION -> LlmRequestType.GRADING;
        };
    }

    public record Context(String question) {
    }

    public record TestResponse(String answer, long usedCount, String roomStatus) {
    }
}
