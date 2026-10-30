package com.shelved.common.entity;

import com.shelved.common.enums.LlmCallStatus;
import com.shelved.common.enums.LlmRequestType;
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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * Claude API 호출 1회의 사용량 기록. 재시도도 각각 기록한다.
 * 사용량을 확인할 수 없으면 토큰·비용을 0이 아닌 null로 두고 usage_known=false로 기록한다.
 */
@Entity
@Table(name = "llm_usage_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LlmUsageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false)
    private LlmRequestType requestType;

    // 호출한 모델 식별자. 모델별 토큰 단가로 예상 비용을 계산하는 기준
    @Column(name = "model", nullable = false)
    private String model;

    // 세션(테스트 세션 포함)을 삭제해도 로그는 남도록 DB에서 ON DELETE SET NULL로 처리한다
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id")
    private MysteryCase mysteryCase;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    // USD
    @Column(name = "estimated_cost", precision = 12, scale = 6)
    private BigDecimal estimatedCost;

    @Enumerated(EnumType.STRING)
    @Column(name = "call_status", nullable = false)
    private LlmCallStatus callStatus;

    // 호출 실패 시 오류 구분 (예: 시간 초과, 서비스 오류)
    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "usage_known", nullable = false)
    private boolean usageKnown;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * 사용량 기록. 입력·출력 토큰이 모두 있을 때만 usage_known=true
     */
    public LlmUsageLog(
            LlmRequestType requestType,
            String model,
            Room room,
            MysteryCase mysteryCase,
            Integer inputTokens,
            Integer outputTokens,
            BigDecimal estimatedCost,
            LlmCallStatus callStatus,
            String errorCode,
            LocalDateTime createdAt
    ) {
        this.requestType = requestType;
        this.model = model;
        this.room = room;
        this.mysteryCase = mysteryCase;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.estimatedCost = estimatedCost;
        this.callStatus = callStatus;
        this.errorCode = errorCode;
        this.usageKnown = inputTokens != null && outputTokens != null;
        this.createdAt = createdAt;
    }
}
