package com.shelved.common.entity;

import com.shelved.common.entity.json.ElementResult;
import com.shelved.common.enums.GradingStatus;
import com.shelved.common.enums.Verdict;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "accusations",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_accusations_room_id_request_id",
                columnNames = {"room_id", "request_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Accusation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submitted_by_user_id", nullable = false)
    private User submittedByUser;

    // 지목한 인물
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "suspect_id", nullable = false)
    private Suspect suspect;

    @Column(name = "trick_explanation", nullable = false, columnDefinition = "text")
    private String trickExplanation;

    // 서버 접수 시각
    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    // 접수 시점의 누적 진행 시간. 제한시간 미만이면 유효한 답안이며, 정답이면 기록 시간이 된다
    @Column(name = "submitted_active_ms", nullable = false)
    private long submittedActiveMs;

    @Enumerated(EnumType.STRING)
    @Column(name = "grading_status", nullable = false)
    private GradingStatus gradingStatus;

    @Column(name = "request_id", nullable = false)
    private String requestId;

    // 채점 완료 시에만 채운다
    @Column(name = "culprit_correct")
    private Boolean culpritCorrect;

    @Enumerated(EnumType.STRING)
    @Column(name = "verdict")
    private Verdict verdict;

    // 항목별 판정. 정답 보호: 플레이어에게는 feedback만 반환
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "element_results", columnDefinition = "jsonb")
    private List<ElementResult> elementResults;

    // 난이도별로 구성한 플레이어용 피드백
    @Column(name = "feedback", columnDefinition = "text")
    private String feedback;

    /**
     * 채점 대기(PENDING) 지목 접수
     */
    public Accusation(
            Room room,
            User submittedByUser,
            Suspect suspect,
            String requestId,
            String trickExplanation,
            LocalDateTime submittedAt,
            long submittedActiveMs
    ) {
        this.room = room;
        this.submittedByUser = submittedByUser;
        this.suspect = suspect;
        this.requestId = requestId;
        this.trickExplanation = trickExplanation;
        this.submittedAt = submittedAt;
        this.submittedActiveMs = submittedActiveMs;
        this.gradingStatus = GradingStatus.PENDING;
    }

    /**
     * 채점 완료 결과 반영
     */
    public void completeGrading(
            boolean culpritCorrect,
            Verdict verdict,
            List<ElementResult> elementResults,
            String feedback
    ) {
        this.gradingStatus = GradingStatus.COMPLETED;
        this.culpritCorrect = culpritCorrect;
        this.verdict = verdict;
        this.elementResults = elementResults;
        this.feedback = feedback;
    }

    /**
     * 채점 실패. 기회를 차감하지 않으며 같은 답안으로 재채점할 수 있다
     */
    public void markGradingError() {
        this.gradingStatus = GradingStatus.ERROR;
    }
}
