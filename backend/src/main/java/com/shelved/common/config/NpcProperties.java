package com.shelved.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * NPC 대화 설정값 (shelved.npc)
 *
 * @param historyMessageLimit NPC 프롬프트에 넣는 대화 이력의 최대 메시지 수 (질문·답변 각각 1개). 그보다 오래된 메시지는 넣지 않는다
 */
@ConfigurationProperties(prefix = "shelved.npc")
public record NpcProperties(
        int historyMessageLimit
) {

    public NpcProperties {
        if (historyMessageLimit <= 0) {
            throw new IllegalArgumentException("shelved.npc.history-message-limit는 1 이상이어야 합니다.");
        }
    }
}
