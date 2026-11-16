package com.shelved.domain.npc.service;

import static com.shelved.common.exception.ErrorCode.NPC_NOT_CONVERSABLE;

import com.shelved.common.entity.Suspect;
import com.shelved.common.exception.CustomException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 질문할 수 있는 NPC인지 확인한다. 사전 검사와 접수 트랜잭션(NpcAskHandler.prepare)이 같은 기준을 쓴다
 */
@Slf4j
@Component
public class NpcSuspectValidator {

    /**
     * canConverse=false면 400 NPC_NOT_CONVERSABLE. 대화 가능인데 persona_prompt가 비어 있으면(콘텐츠 반영 누락)
     * 설정 없이 답하게 하지 않도록 같은 코드로 거부하고 경고 로그를 남긴다
     */
    public void validateConversable(Suspect suspect) {
        if (!suspect.isCanConverse()) {
            throw new CustomException(NPC_NOT_CONVERSABLE);
        }
        if (suspect.getPersonaPrompt() == null || suspect.getPersonaPrompt().isBlank()) {
            log.warn("대화 가능 인물의 persona_prompt가 비어 있음 suspectId={}", suspect.getId());
            throw new CustomException(NPC_NOT_CONVERSABLE);
        }
    }
}
