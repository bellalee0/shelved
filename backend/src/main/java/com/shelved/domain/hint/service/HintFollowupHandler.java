package com.shelved.domain.hint.service;

import static com.shelved.common.exception.ErrorCode.HINT_FOLLOWUP_ALREADY_USED;

import com.shelved.common.entity.AiRequest;
import com.shelved.common.entity.Hint;
import com.shelved.common.entity.HintFollowup;
import com.shelved.common.entity.Room;
import com.shelved.common.enums.AiRequestType;
import com.shelved.common.exception.CustomException;
import com.shelved.domain.airequest.service.AiRequestCommand;
import com.shelved.domain.airequest.service.AiRequestHandler;
import com.shelved.domain.airequest.service.AiRequestSuccess;
import com.shelved.domain.hint.dto.HintFollowupResponse;
import com.shelved.domain.hint.repository.HintFollowupRepository;
import com.shelved.domain.hint.repository.HintRepository;
import com.shelved.infra.llm.LlmRequest;
import com.shelved.infra.llm.LlmResponse;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 무료 보충 질문(HINT_FOLLOWUP)의 AiRequestHandler. 대상 키는 hintId(힌트별 동시 1건, 다른 힌트의 보충 질문은 동시에 가능).
 * 힌트 횟수를 검사·차감하지 않는다 (힌트 횟수가 0이어도 가능). 힌트당 1회는 hint_followups 행(uk_hint_followups_hint_id)으로 정한다.
 * "새 힌트가 필요합니다" 같은 안내 답변도 정상 답변으로 저장하고 사용 처리한다
 */
@Component
@RequiredArgsConstructor
public class HintFollowupHandler implements AiRequestHandler<HintFollowupContext> {

    private final HintRepository hintRepository;
    private final HintFollowupRepository hintFollowupRepository;
    private final HintPromptBuilder hintPromptBuilder;
    private final Clock clock;

    @Override
    public AiRequestType type() {
        return AiRequestType.HINT_FOLLOWUP;
    }

    /**
     * 접수 트랜잭션 안(rooms 행 잠금). 이 세션의 힌트인지(404 HINT_NOT_FOUND), 보충 질문을 이미 썼는지(409 HINT_FOLLOWUP_ALREADY_USED) 최종 확인한다
     */
    @Override
    public HintFollowupContext prepare(Room room, AiRequestCommand command) {

        Hint hint = hintRepository.findHintOfRoom(command.targetId(), room.getId());
        validateFollowupUnused(hint.getId());

        return new HintFollowupContext(
                command.requestId(),
                hint.getId(),
                room.getMysteryCase().getGlossary(),
                hint.getQuestion(),
                hint.getContent(),
                command.input().question()
        );
    }

    @Override
    public LlmRequest buildLlmRequest(HintFollowupContext context) {
        return hintPromptBuilder.buildFollowup(context);
    }

    /**
     * 확정 트랜잭션 안(rooms 행 잠금). 답변을 검증하고, 이미 사용되지 않았는지 다시 확인한 뒤 보충 질문을 저장한다
     */
    @Override
    public AiRequestSuccess complete(Room room, AiRequest aiRequest, HintFollowupContext context, LlmResponse response) {

        String content = HintAnswerValidator.validate(response.text(), "보충 질문");
        validateFollowupUnused(context.hintId());

        HintFollowup followup = hintFollowupRepository.saveAndFlush(new HintFollowup(
                hintRepository.getReferenceById(context.hintId()),
                room.getHostUser(),
                context.requestId(),
                context.followupQuestion(),
                content,
                LocalDateTime.now(clock)
        ));

        return new AiRequestSuccess(followup.getId(), HintFollowupResponse.from(followup));
    }

    private void validateFollowupUnused(Long hintId) {
        if (hintFollowupRepository.existsByHintId(hintId)) {
            throw new CustomException(HINT_FOLLOWUP_ALREADY_USED);
        }
    }
}
