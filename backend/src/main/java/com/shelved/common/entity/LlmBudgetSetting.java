package com.shelved.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 서비스 전체에 하나뿐인 AI 월 예산 설정. 행이 없으면 미설정이다.
 * 단일 행 유지는 저장하는 쪽에서 기존 행을 갱신하는 방식으로 지킨다.
 */
@Entity
@Table(name = "llm_budget_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LlmBudgetSetting {

    public static final String DEFAULT_CURRENCY = "USD";
    public static final String DEFAULT_TIMEZONE = "Asia/Seoul";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "monthly_limit", nullable = false, precision = 12, scale = 2)
    private BigDecimal monthlyLimit;

    @Column(name = "currency", nullable = false)
    private String currency;

    // 경고 임계값 (1~100)
    @Column(name = "warning_percent", nullable = false)
    private int warningPercent;

    // 월 집계 기준 시간대
    @Column(name = "timezone", nullable = false)
    private String timezone;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public LlmBudgetSetting(BigDecimal monthlyLimit, int warningPercent, LocalDateTime updatedAt) {
        this.monthlyLimit = monthlyLimit;
        this.warningPercent = warningPercent;
        this.currency = DEFAULT_CURRENCY;
        this.timezone = DEFAULT_TIMEZONE;
        this.updatedAt = updatedAt;
    }
}
