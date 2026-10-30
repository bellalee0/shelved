package com.shelved.common.entity.json;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 플레이어가 보낸 AI 요청 입력 (AI_REQUESTS.input_json). 재시도 시 같은 입력인지 비교하는 기준이다.
 * NPC_ASK·HINT·HINT_FOLLOWUP은 question, ACCUSATION은 suspectId·trickExplanation만 채운다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiRequestInput(
        String question,
        Long suspectId,
        String trickExplanation
) {

    public static AiRequestInput question(String question) {
        return new AiRequestInput(question, null, null);
    }

    public static AiRequestInput accusation(Long suspectId, String trickExplanation) {
        return new AiRequestInput(null, suspectId, trickExplanation);
    }
}
