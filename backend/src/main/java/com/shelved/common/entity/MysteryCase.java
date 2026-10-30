package com.shelved.common.entity;

import com.shelved.common.entity.json.GlossaryTerm;
import com.shelved.common.enums.CaseStatus;
import com.shelved.common.enums.Difficulty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 사건 (cases). JPQL 예약어 CASE와 겹치지 않도록 클래스명을 MysteryCase로 둔다.
 * 초안 단계에서는 카드·콘텐츠 필드가 비어 있을 수 있으므로 관리 필드 외에는 nullable이다.
 */
@Entity
@Table(name = "cases")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MysteryCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CaseStatus status;

    // 카드 정보
    @Column(name = "display_title")
    private String displayTitle;

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty")
    private Difficulty difficulty;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "keyword_tags", columnDefinition = "jsonb")
    private List<String> keywordTags;

    @Column(name = "cover_image_url")
    private String coverImageUrl;

    // 원작 정보 (정답 보호: 리빌 전 플레이어 응답에 포함하지 않음)
    @Column(name = "source_novel_title")
    private String sourceNovelTitle;

    @Column(name = "source_novel_author")
    private String sourceNovelAuthor;

    @Column(name = "source_novel_intro", columnDefinition = "text")
    private String sourceNovelIntro;

    // 정답 (정답 보호: 리빌 전 플레이어 응답에 포함하지 않음)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "culprit_suspect_id")
    private Suspect culpritSuspect;

    @Column(name = "trick_summary", columnDefinition = "text")
    private String trickSummary;

    // 사전 정보
    @Column(name = "introduction", columnDefinition = "text")
    private String introduction;

    @Column(name = "investigation_goal", columnDefinition = "text")
    private String investigationGoal;

    @Column(name = "background_knowledge", columnDefinition = "text")
    private String backgroundKnowledge;

    @Column(name = "reveal_explanation", columnDefinition = "text")
    private String revealExplanation;

    // 생성 결과의 표기에만 쓰고 플레이어에게 반환하지 않는다
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "glossary", columnDefinition = "jsonb")
    private List<GlossaryTerm> glossary;

    // 공개 사건을 복제해 만든 사건이면 원본
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cloned_from_case_id")
    private MysteryCase clonedFromCase;

    // 마지막 콘텐츠·맵·배치 변경 시각. 테스트 완료 확인의 기준
    @Column(name = "content_updated_at")
    private LocalDateTime contentUpdatedAt;

    // 현재 내용의 테스트 완료 확인 시각. 콘텐츠·맵·배치가 바뀌면 null
    @Column(name = "test_verified_at")
    private LocalDateTime testVerifiedAt;

    // 원문·초안·제작 상태 변경을 구분하는 서버 버전 (docs/api/admin-casebuilder.md 제작 상태 변경과 재시도)
    @Column(name = "chat_context_revision", nullable = false)
    private long chatContextRevision;

    // 관리자 사건 목록의 최근 수정순 정렬 기준
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 빈 DRAFT 사건 생성
     */
    public MysteryCase(LocalDateTime createdAt) {
        this.status = CaseStatus.DRAFT;
        this.chatContextRevision = 0L;
        this.updatedAt = createdAt;
    }

    public void updateCardInfo(String displayTitle, Difficulty difficulty, List<String> keywordTags) {
        this.displayTitle = displayTitle;
        this.difficulty = difficulty;
        this.keywordTags = keywordTags;
    }

    public void updateSourceNovel(String title, String author, String intro) {
        this.sourceNovelTitle = title;
        this.sourceNovelAuthor = author;
        this.sourceNovelIntro = intro;
    }

    public void updateBriefing(String introduction, String investigationGoal, String backgroundKnowledge) {
        this.introduction = introduction;
        this.investigationGoal = investigationGoal;
        this.backgroundKnowledge = backgroundKnowledge;
    }

    /**
     * 범인·트릭 요약·리빌 해설 변경. 범인은 이 사건의 인물이어야 하며 검증은 호출하는 쪽에서 한다.
     */
    public void updateSolution(Suspect culpritSuspect, String trickSummary, String revealExplanation) {
        this.culpritSuspect = culpritSuspect;
        this.trickSummary = trickSummary;
        this.revealExplanation = revealExplanation;
    }

    public void updateGlossary(List<GlossaryTerm> glossary) {
        this.glossary = glossary;
    }

    public void updateCoverImageUrl(String coverImageUrl) {
        this.coverImageUrl = coverImageUrl;
    }

    /**
     * 상태 변경. 허용되는 전이인지는 호출하는 쪽에서 검사한다.
     */
    public void changeStatus(CaseStatus status) {
        this.status = status;
    }

    public void markUpdated(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
