package com.shelved.domain.accusation.service;

import com.shelved.common.entity.Room;
import com.shelved.common.entity.json.AiRequestInput;
import com.shelved.common.jwt.AuthUser;
import com.shelved.domain.accusation.dto.AccusationCreateRequest;
import com.shelved.domain.accusation.dto.AccusationListResponse;
import com.shelved.domain.airequest.service.AiRequestCommand;
import com.shelved.domain.airequest.service.AiRequestExecutor;
import com.shelved.domain.airequest.service.AiRequestResult;
import com.shelved.domain.room.service.RoomAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccusationService {

    private final AccusationPrecheckService accusationPrecheckService;
    private final AiRequestExecutor aiRequestExecutor;
    private final AccusationHandler accusationHandler;
    private final AccusationQueryService accusationQueryService;
    private final AccusationUsageCounter accusationUsageCounter;
    private final RoomAccessService roomAccessService;

    /**
     * 지목 제출·재채점. 트랜잭션 없이 실행한다 (채점 AI 호출 중 트랜잭션·잠금을 잡지 않음, backend.md 13).
     * 지목은 정답 제출 행위이므로 질문 분류(QUESTION_FILTER)를 하지 않는다
     *
     * <ol>
     *     <li>새 답안이면 마감 후 제출인지 먼저 확인한다 (409 ROOM_TIME_EXPIRED)</li>
     *     <li>AiRequestExecutor: 접수(새 답안은 플레이 공통 검사, 재채점은 종료 전 확인·최소 간격 / 세션당 동시 1건 / 기회 / 대체)
     *     → 채점 → 확정(결과 반영). 같은 requestId가 성공·처리 중이면 저장된 결과(200)·처리 상태(202)</li>
     * </ol>
     */
    public AiRequestResult createAccusation(
            AuthUser authUser,
            Long roomId,
            AccusationCreateRequest request,
            String connectionId
    ) {
        AiRequestCommand command = new AiRequestCommand(
                roomId,
                request.requestId(),
                null,
                AiRequestInput.accusation(request.suspectId(), request.trickExplanation()),
                connectionId
        );

        accusationPrecheckService.validateNewAnswerTime(authUser, roomId, request.requestId());

        return aiRequestExecutor.execute(authUser, command, accusationHandler);
    }

    /**
     * 지목 기록과 횟수. 참가자만, 모든 세션 상태에서 조회한다
     */
    @Transactional(readOnly = true)
    public AccusationListResponse getAccusationList(AuthUser authUser, Long roomId) {

        Room room = roomAccessService.getAccessibleRoom(roomId, authUser);

        return new AccusationListResponse(
                accusationUsageCounter.countAccusationUsage(room.getId()),
                accusationQueryService.getAccusationResponseList(room.getId())
        );
    }
}
