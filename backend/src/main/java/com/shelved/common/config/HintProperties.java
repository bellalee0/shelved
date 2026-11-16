package com.shelved.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 힌트 설정값 (shelved.hint). 힌트 횟수 한도는 shelved.game.hint-limit(GameProperties)에 있다
 *
 * @param npcMessageLimit 힌트 프롬프트에 넣는 NPC별 대화 원문의 최대 메시지 수 (질문·진술 각각 1개, 최근 것부터)
 * @param historyLimit    힌트 프롬프트에 넣는 이전 힌트(질문·답변 쌍)의 최대 개수 (최근 것부터)
 */
@ConfigurationProperties(prefix = "shelved.hint")
public record HintProperties(
        int npcMessageLimit,
        int historyLimit
) {

    public HintProperties {
        if (npcMessageLimit <= 0) {
            throw new IllegalArgumentException("shelved.hint.npc-message-limit는 1 이상이어야 합니다.");
        }
        if (historyLimit <= 0) {
            throw new IllegalArgumentException("shelved.hint.history-limit는 1 이상이어야 합니다.");
        }
    }
}
