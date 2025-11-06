package com.kanban.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 規則執行歷史 - 記錄規則執行結果
 */
@Entity
@Table(name = "rule_executions",
       indexes = {
           @Index(name = "idx_execution_rule_time", columnList = "rule_id, executed_at"),
           @Index(name = "idx_execution_card_time", columnList = "card_id, executed_at"),
           @Index(name = "idx_execution_status_time", columnList = "status, executed_at")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class RuleExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id", nullable = false)
    private Rule rule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id", nullable = false)
    private Card card;

    /**
     * 執行狀態
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExecutionStatus status;

    /**
     * 執行前的卡片狀態
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status")
    private Card.CardStatus previousStatus;

    /**
     * 執行後的卡片狀態
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "new_status")
    private Card.CardStatus newStatus;

    /**
     * 條件評估結果 - JSON 格式
     */
    @Column(name = "condition_result", columnDefinition = "TEXT")
    private String conditionResult;

    /**
     * 觸發時的股票資料快照 - JSON 格式
     */
    @Column(name = "stock_snapshot", columnDefinition = "TEXT")
    private String stockSnapshot;

    /**
     * 執行訊息 - 成功或錯誤訊息
     */
    @Column(columnDefinition = "TEXT")
    private String message;

    /**
     * 是否已發送通知
     */
    @Column(name = "notification_sent", nullable = false)
    @Builder.Default
    private Boolean notificationSent = false;

    /**
     * 執行耗時（毫秒）
     */
    @Column(name = "execution_time_ms")
    private Long executionTimeMs;

    @CreatedDate
    @Column(name = "executed_at", nullable = false, updatable = false)
    private LocalDateTime executedAt;

    /**
     * 執行狀態
     */
    public enum ExecutionStatus {
        SUCCESS("成功"),
        FAILED("失敗"),
        SKIPPED("已跳過"),
        COOLDOWN("冷卻中");

        private final String displayName;

        ExecutionStatus(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
