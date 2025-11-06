package com.kanban.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 規則實體 - 自動化規則定義
 * 支援 SpEL 表達式評估和自訂規則
 */
@Entity
@Table(name = "rules",
       indexes = {
           @Index(name = "idx_rule_user_enabled", columnList = "user_id, enabled, rule_type"),
           @Index(name = "idx_rule_trigger_target", columnList = "trigger_event, target_status")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Rule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false)
    @Builder.Default
    private RuleType ruleType = RuleType.CUSTOM;

    /**
     * SpEL 表達式 - 用於評估規則條件
     * 例如: "price > 100 && volume > 1000000"
     * 或: "rsi < 30 || macd > 0"
     */
    @Column(name = "condition_expression", nullable = false, columnDefinition = "TEXT")
    private String conditionExpression;

    /**
     * 觸發事件類型
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_event", nullable = false)
    private TriggerEvent triggerEvent;

    /**
     * 目標狀態 - 規則觸發後卡片應轉換的狀態
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "target_status", nullable = false)
    private Card.CardStatus targetStatus;

    /**
     * 是否啟用
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    /**
     * 冷卻時間（秒） - 防止重複觸發
     */
    @Column(name = "cooldown_seconds", nullable = false)
    @Builder.Default
    private Integer cooldownSeconds = 3600;

    /**
     * 優先級 - 數字越小優先級越高
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer priority = 5;

    /**
     * 是否發送通知
     */
    @Column(name = "send_notification", nullable = false)
    @Builder.Default
    private Boolean sendNotification = true;

    /**
     * 通知訊息模板
     */
    @Column(name = "notification_template", columnDefinition = "TEXT")
    private String notificationTemplate;

    /**
     * 規則標籤 - JSON 格式，用於分類和篩選
     */
    @Column(columnDefinition = "TEXT")
    private String tags;

    /**
     * 規則參數 - JSON 格式，用於儲存額外配置
     */
    @Column(columnDefinition = "TEXT")
    private String parameters;

    /**
     * 上次執行時間
     */
    @Column(name = "last_executed_at")
    private LocalDateTime lastExecutedAt;

    /**
     * 觸發次數
     */
    @Column(name = "trigger_count", nullable = false)
    @Builder.Default
    private Long triggerCount = 0L;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * 規則類型
     */
    public enum RuleType {
        PREDEFINED("預設規則"),      // 系統預設規則
        CUSTOM("自訂規則"),          // 使用者自訂規則
        TEMPLATE("規則模板");        // 規則模板

        private final String displayName;

        RuleType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * 觸發事件類型
     */
    public enum TriggerEvent {
        PRICE_CHANGE("價格變動"),
        VOLUME_SPIKE("成交量異常"),
        TECHNICAL_INDICATOR("技術指標"),
        PRICE_ALERT("價格警示"),
        TIME_BASED("定時觸發");

        private final String displayName;

        TriggerEvent(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
