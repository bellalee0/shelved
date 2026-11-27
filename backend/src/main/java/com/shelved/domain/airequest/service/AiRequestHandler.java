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
 * <p>유형별 확장 지점(기본 구현이 있어 NPC 질문·힌트·보충 질문은 재정의하지 않는다)</p>
 * <ul>
 *     <li>retryRequiresPlayableRoom — 실패한 요청의 재시도에 플레이 공통 검사를 할지 (지목 재채점은 하지 않는다)</li>
 *     <li>onFailed — 요청 행이 FAILED가 된 모든 경로의 후처리 (지목 행 ERROR)</li>
 * </ul>
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
     * 실패한 요청을 같은 requestId·입력으로 다시 실행할 때, 새 요청과 같은 플레이 공통 검사(RoomPlayService.acquirePlayableRoom:
     * 연결 헤더·진행 중·남은 시간·현재 연결·접속 확인)를 할지. 기본 true.
     * false면 접수 트랜잭션은 세션이 종료되지 않았는지만 확인하고(종료면 409 ROOM_NOT_IN_PROGRESS), 접속 확인(last_seen_at)·재개를 하지 않는다.
     * 유형 고유의 재시도 조건(최소 간격 등)은 prepare에서 검사한다
     */
    default boolean retryRequiresPlayableRoom() {
        return true;
    }

    /**
     * 요청 행을 FAILED로 바꾼 직후, 같은 트랜잭션(rooms 행 잠금 안)에서 호출된다. 모델 실패·형식 오류·예상하지 못한 예외의 정리,
     * 작업 시간 제한·서버 재시작 복구, 세션 종료 후 도착한 응답 모두 이 지점을 지난다.
     * 복구 경로에는 접수 컨텍스트가 없으므로 요청 행만으로 처리한다. 기본은 아무것도 하지 않는다
     */
    default void onFailed(AiRequest aiRequest) {
    }

    /**
     * 실패 응답(502 유형별 코드)의 detail. 기본은 { requestId }
     */
    default Object failureDetail(String requestId, C context) {
        return new AiRequestFailureDetail(requestId);
    }
}
