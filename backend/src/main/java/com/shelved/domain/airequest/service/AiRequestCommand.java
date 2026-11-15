package com.shelved.domain.airequest.service;

import com.shelved.common.entity.json.AiRequestInput;
import java.util.Objects;

/**
 * 도메인 API가 받은 AI 요청 (유형은 AiRequestHandler.type()으로 정한다)
 *
 * @param roomId       세션
 * @param requestId    클라이언트가 만든 요청 식별자 (세션 안에서 유형과 관계없이 유일)
 * @param targetId     NPC_ASK: suspectId, HINT_FOLLOWUP: hintId, 그 외 null. 유형과 함께 "대상별 동시 1건"의 기준
 * @param input        플레이어 입력. 같은 requestId 재전송이 같은 요청인지 비교하는 기준
 * @param connectionId X-Connection-Id (새 요청·재시도의 플레이 공통 검사에 쓴다)
 */
public record AiRequestCommand(
        Long roomId,
        String requestId,
        Long targetId,
        AiRequestInput input,
        String connectionId
) {

    public AiRequestCommand {
        Objects.requireNonNull(roomId, "roomId");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(input, "input");
    }

    // connectionId·입력 내용이 로그에 섞이지 않도록 record 기본 toString을 대체한다
    @Override
    public String toString() {
        return "AiRequestCommand[roomId=" + roomId + ", requestId=" + requestId + ", targetId=" + targetId + "]";
    }
}
