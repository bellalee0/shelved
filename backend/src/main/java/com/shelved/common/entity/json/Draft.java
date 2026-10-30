package com.shelved.common.entity.json;

import com.shelved.common.enums.ClueType;
import com.shelved.common.enums.Difficulty;
import java.util.List;

/**
 * 사건 제작 초안 (CASE_DRAFTS.draft_json). 구조는 docs/api/admin-draft-schema.md를 따른다.
 * 관리자 API 응답의 draft, 제작 채팅 AI tool 인자와 같은 구조이므로 필드명을 바꾸지 않는다.
 */
public record Draft(
        int schemaVersion,
        Card card,
        Source source,
        Briefing briefing,
        List<SuspectItem> suspects,
        List<EvidenceItem> evidences,
        Trick trick,
        String revealExplanation,
        List<GlossaryTerm> glossary
) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    /**
     * 새 사건의 빈 초안. 정해지지 않은 값은 null 또는 빈 배열
     */
    public static Draft empty() {
        return new Draft(
                CURRENT_SCHEMA_VERSION,
                new Card(null, null, List.of()),
                new Source(null, null, null),
                new Briefing(null, null, null),
                List.of(),
                List.of(),
                new Trick(null, List.of()),
                null,
                List.of()
        );
    }

    /** 카드 정보 */
    public record Card(
            String displayTitle,
            Difficulty difficulty,
            List<String> keywordTags
    ) {
    }

    /** 원작 정보 */
    public record Source(
            String novelTitle,
            String novelAuthor,
            String novelIntro
    ) {
    }

    /** 사전 정보 */
    public record Briefing(
            String introduction,
            String investigationGoal,
            String backgroundKnowledge
    ) {
    }

    /** 등장인물. entityId는 확정 후 연결된 SUSPECTS.id */
    public record SuspectItem(
            String key,
            Long entityId,
            String name,
            String role,
            boolean canConverse,
            boolean isCulprit,
            String spriteKey,
            Persona persona
    ) {
    }

    /** NPC 지식·진술·추궁 방침. 확정 시 SUSPECTS.persona_prompt로 변환 */
    public record Persona(
            String speechStyle,
            List<Fact> knownFacts,
            List<Fact> unknownFacts,
            List<Fact> statements,
            List<Fact> hiddenFacts,
            List<Lie> lies,
            List<Fact> silenceTopics,
            String pressureResponse,
            List<StatementChange> statementChanges
    ) {
    }

    /** 근거가 붙은 설정 한 줄 */
    public record Fact(
            String text,
            List<SourceRef> sourceRefs
    ) {
    }

    /** 설정의 근거가 되는 원문 위치와 인용 */
    public record SourceRef(
            String location,
            String quote
    ) {
    }

    /** 정해진 거짓말 */
    public record Lie(
            String topic,
            String lie,
            String truth,
            List<SourceRef> sourceRefs
    ) {
    }

    /** 사건 성립에 필요한 진술 변경 조건 */
    public record StatementChange(
            String condition,
            String change,
            List<SourceRef> sourceRefs
    ) {
    }

    /** 물증. entityId는 확정 후 EVIDENCES.id */
    public record EvidenceItem(
            String key,
            Long entityId,
            String name,
            String description,
            List<SourceRef> sourceRefs
    ) {
    }

    /** 트릭 요약과 정답 핵심 요소 */
    public record Trick(
            String summary,
            List<TrickElementItem> elements
    ) {
    }

    /** 트릭 핵심 요소. entityId는 확정 후 CASE_TRICK_ELEMENTS.id */
    public record TrickElementItem(
            String key,
            Long entityId,
            Integer orderIndex,
            String description,
            Rubric rubric,
            List<SupportingClue> supportingClues
    ) {
    }

    /** 초안의 판정 기준. 확정 시 키를 엔티티 ID로 바꿔 GradingRubric으로 저장 */
    public record Rubric(
            List<RubricItem> items,
            EasyFeedback easyFeedback
    ) {
    }

    /** 쉬움 난이도 피드백 참고. 초안에서는 물증·인물 키로 참조 */
    public record EasyFeedback(
            String guideQuestion,
            List<String> evidenceKeys,
            List<String> suspectKeys
    ) {
    }

    /** 요소를 자백 없이 추론할 수 있게 하는 단서 (관리자 검수용). refKey는 물증 키 또는 인물 키 */
    public record SupportingClue(
            ClueType type,
            String refKey,
            String note
    ) {
    }
}
