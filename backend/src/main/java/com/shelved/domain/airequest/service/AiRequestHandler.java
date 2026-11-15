package com.shelved.domain.airequest.service;

import com.shelved.common.entity.AiRequest;
import com.shelved.common.entity.Room;
import com.shelved.common.enums.AiRequestType;
import com.shelved.domain.airequest.dto.AiRequestFailureDetail;
import com.shelved.infra.llm.LlmRequest;
import com.shelved.infra.llm.LlmResponse;

/**
 * AI 요청 유형별로 달라지는 부분. 도메인(NPC 질문·힌트·보충 질문·지목)이 구현하고 AiRequestExecutor에 넘긴다.
 *
 * <p>호출 순서와 트랜잭션</p>
 * <ol>
 *     <li>prepare — 접수 트랜잭션 안. rooms 행이 잠겨 있고 접근·플레이 공통 검사·대상별 동시 1건 검사를 마친 뒤 호출된다.
 *     한도(사용 확정 + 처리 중 임시 확보분) 검사를 하고, AI 호출에 필요한 데이터를 읽어 컨텍스트로 돌려준다.
 *     거부하려면 CustomException을 던진다(요청 행은 저장되지 않는다)</li>
 *     <li>buildLlmRequest — 트랜잭션 밖. 컨텍스트만으로 프롬프트를 만든다 (DB를 읽지 않는다)</li>
 *     <li>complete — 확정 트랜잭션 안. rooms 행이 잠겨 있고, 세션이 끝나지 않았고 이 실행이 최신인 것이 확인된 뒤 호출된다.
 *     응답 형식을 검증하고(잘못되면 AiResponseInvalidException) 게임 데이터를 저장한 뒤 플레이어용 성공 응답을 돌려준다.
 *     돌려준 응답은 result_json 스냅샷으로 저장되어 재전송·상태 조회에 그대로 쓰인다</li>
 * </ol>
 *
 * <p>임시 확보: 처리 중인 AI_REQUESTS 행(PROCESSING) 자체가 임시 확보다. 남은 횟수는 사용 확정 + 같은 유형 PROCESSING 수로 계산한다.
 * 실패·복구로 FAILED가 되면 별도 해제 없이 확보가 풀린다. 별도 행으로 확보하는 도메인은 이 규칙을 따르도록 맞춘다.</p>
 *
 * @param <C> 접수 시 읽어 둔 AI 호출 컨텍스트 (메모리에만 있고 저장하지 않는다)
 */
public interface AiRequestHandler<C> {

    AiRequestType type();

    C prepare(Room room, AiRequestCommand command);

    LlmRequest buildLlmRequest(C context);

    AiRequestSuccess complete(Room room, AiRequest aiRequest, C context, LlmResponse response);

    /**
     * 실패 응답(502 유형별 코드)의 detail. 기본은 { requestId }
     */
    default Object failureDetail(String requestId, C context) {
        return new AiRequestFailureDetail(requestId);
    }
}
