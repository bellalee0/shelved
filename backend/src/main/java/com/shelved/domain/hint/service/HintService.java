package com.shelved.domain.hint.service;

import com.shelved.common.entity.json.AiRequestInput;
import com.shelved.common.enums.AiRequestType;
import com.shelved.common.jwt.AuthUser;
import com.shelved.domain.airequest.service.AiRequestCommand;
import com.shelved.domain.airequest.service.AiRequestExecutor;
import com.shelved.domain.airequest.service.AiRequestPrecheckService;
import com.shelved.domain.airequest.service.AiRequestResult;
import com.shelved.domain.airequest.service.QuestionFilterService;
import com.shelved.domain.hint.dto.HintCreateRequest;
import com.shelved.infra.llm.LlmCallContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HintService {

    private final AiRequestPrecheckService aiRequestPrecheckService;
    private final HintPrecheckService hintPrecheckService;
    private final QuestionFilterService questionFilterService;
    private final AiRequestExecutor aiRequestExecutor;
    private final HintHandler hintHandler;

    /**
     * 새 힌트 요청. 트랜잭션 없이 실행한다 (분류·답변 AI 호출 중 트랜잭션·잠금을 잡지 않음, backend.md 13)
     *
     * <ol>
     *     <li>같은 requestId가 이미 성공·처리 중이면 분류·사전 검사 없이 접수 단계로 보내 저장된 결과(200)·처리 상태(202)를 받는다.
     *     한도를 다 쓴 뒤의 재전송도 저장된 스냅샷을 받는다. 입력이 다르면 409 AI_REQUEST_ID_REUSED</li>
     *     <li>새 요청·실패 재시도: 플레이 공통 검사·남은 힌트(짧은 트랜잭션) → 질문 분류(트랜잭션 밖, 차단 400·실패 502)</li>
     *     <li>AiRequestExecutor: 접수(최종 검사·세션당 동시 1건·한도) → 힌트 생성 → 확정(1회 사용)</li>
     * </ol>
     * 차단·분류 실패 시 AI_REQUESTS 행과 힌트를 만들지 않는다 (차감 없음)
     */
    public AiRequestResult createHint(AuthUser authUser, Long roomId, HintCreateRequest request, String connectionId) {

        AiRequestCommand command = new AiRequestCommand(
                roomId, request.requestId(), null, AiRequestInput.question(request.question()), connectionId);

        if (!aiRequestPrecheckService.isAlreadyAccepted(authUser, command, AiRequestType.HINT)) {
            Long caseId = hintPrecheckService.validateHintRequestable(authUser, roomId, connectionId);
            questionFilterService.validateQuestion(request.question(), request.requestId(),
                    LlmCallContext.game(roomId, caseId), AiRequestType.HINT.getFailureCode());
        }

        return aiRequestExecutor.execute(authUser, command, hintHandler);
    }
}
