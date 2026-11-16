package com.shelved.domain.npc.service;

import com.shelved.common.entity.json.GlossaryTerm;
import com.shelved.common.enums.Difficulty;
import com.shelved.common.enums.MessageSenderType;
import java.util.List;

/**
 * NPC 질문 1건의 AI 호출 컨텍스트. 접수 트랜잭션에서 읽어 메모리에만 두고 저장하지 않는다 (input_json에는 질문만 저장).
 * persona·대화 이력 등 내부 설정이 담기므로 로그에 남기지 않는다
 *
 * @param historyList 이 NPC와의 최근 대화 (sequenceNo 오름차순, 최대 shelved.npc.history-message-limit개)
 * @param question    이번 질문 원문
 */
public record NpcAskContext(
        String requestId,
        Long suspectId,
        String suspectName,
        String personaPrompt,
        Difficulty difficulty,
        List<GlossaryTerm> glossary,
        List<HistoryMessage> historyList,
        String question
) {

    public NpcAskContext {
        glossary = glossary == null ? List.of() : List.copyOf(glossary);
        historyList = List.copyOf(historyList);
    }

    public record HistoryMessage(
            MessageSenderType senderType,
            String content
    ) {

        @Override
        public String toString() {
            return "HistoryMessage[senderType=" + senderType + "]";
        }
    }

    // 인물 설정·대화 이력·질문이 로그에 섞이지 않도록 record 기본 toString을 대체한다
    @Override
    public String toString() {
        return "NpcAskContext[requestId=" + requestId + ", suspectId=" + suspectId + ", historySize=" + historyList.size() + "]";
    }
}
