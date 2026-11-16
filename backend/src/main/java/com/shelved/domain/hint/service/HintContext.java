package com.shelved.domain.hint.service;

import com.shelved.common.entity.json.GlossaryTerm;
import java.util.List;

/**
 * 새 힌트 1건의 AI 호출 컨텍스트. 접수 트랜잭션에서 읽어 메모리에만 두고 저장하지 않는다 (input_json에는 질문만 저장).
 * 정답(범인·트릭)·대화 원문 등이 담기므로 응답·로그에 넣지 않는다
 *
 * @param caseInfo              사건 정보 (정답 참고 포함)
 * @param inspectedEvidenceList 이 세션에서 조사한 물증 이름 (처음 조사한 순)
 * @param conversationList      대화한 NPC별 최근 대화 원문 (suspectId 순, NPC당 최대 shelved.hint.npc-message-limit개)
 * @param previousHintList      이전 힌트 (오래된 순, 최대 shelved.hint.history-limit개)
 * @param question              이번 질문 원문
 */
public record HintContext(
        String requestId,
        CaseInfo caseInfo,
        List<String> inspectedEvidenceList,
        List<NpcConversation> conversationList,
        List<PreviousHint> previousHintList,
        String question
) {

    public HintContext {
        inspectedEvidenceList = List.copyOf(inspectedEvidenceList);
        conversationList = List.copyOf(conversationList);
        previousHintList = List.copyOf(previousHintList);
    }

    /**
     * @param culpritName 범인 이름 (정답 참고용. 지정되지 않았으면 null)
     */
    public record CaseInfo(
            String title,
            String introduction,
            String investigationGoal,
            String backgroundKnowledge,
            List<GlossaryTerm> glossary,
            List<SuspectInfo> suspectList,
            List<EvidenceInfo> evidenceList,
            String culpritName,
            String trickSummary,
            List<String> trickElementList
    ) {

        public CaseInfo {
            glossary = glossary == null ? List.of() : List.copyOf(glossary);
            suspectList = List.copyOf(suspectList);
            evidenceList = List.copyOf(evidenceList);
            trickElementList = List.copyOf(trickElementList);
        }

        @Override
        public String toString() {
            return "CaseInfo[suspectCount=" + suspectList.size() + ", evidenceCount=" + evidenceList.size() + "]";
        }
    }

    public record SuspectInfo(
            String name,
            String publicIntro
    ) {
    }

    public record EvidenceInfo(
            String name,
            String description
    ) {

        @Override
        public String toString() {
            return "EvidenceInfo[name=" + name + "]";
        }
    }

    /**
     * 한 NPC와의 최근 대화 (sequenceNo 오름차순)
     */
    public record NpcConversation(
            String suspectName,
            List<Message> messageList
    ) {

        public NpcConversation {
            messageList = List.copyOf(messageList);
        }

        @Override
        public String toString() {
            return "NpcConversation[messageCount=" + messageList.size() + "]";
        }
    }

    /**
     * @param fromPlayer true면 플레이어의 질문, false면 NPC의 진술
     */
    public record Message(
            boolean fromPlayer,
            String content
    ) {

        @Override
        public String toString() {
            return "Message[fromPlayer=" + fromPlayer + "]";
        }
    }

    public record PreviousHint(
            String question,
            String content
    ) {

        @Override
        public String toString() {
            return "PreviousHint";
        }
    }

    // 정답·대화 원문·질문이 로그에 섞이지 않도록 record 기본 toString을 대체한다
    @Override
    public String toString() {
        return "HintContext[requestId=" + requestId
                + ", inspectedCount=" + inspectedEvidenceList.size()
                + ", conversationCount=" + conversationList.size()
                + ", previousHintCount=" + previousHintList.size() + "]";
    }
}
