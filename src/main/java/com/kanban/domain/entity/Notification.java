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
 * 通知實體
 */
@Entity
@Table(name = "notifications",
       indexes = {
           @Index(name = "idx_notification_user_created", columnList = "user_id, created_at DESC"),
           @Index(name = "idx_notification_user_read", columnList = "user_id, is_read, created_at DESC")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private NotificationType type = NotificationType.INFO;

    /**
     * 關聯的規則 ID
     */
    @Column(name = "rule_id")
    private String ruleId;

    /**
     * 關聯的卡片 ID
     */
    @Column(name = "card_id")
    private String cardId;

    /**
     * 關聯的股票代碼
     */
    @Column(name = "stock_code", length = 10)
    private String stockCode;

    /**
     * 額外資料（JSON 格式）
     */
    @Column(columnDefinition = "TEXT")
    private String metadata;

    /**
     * 是否已讀
     */
    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private Boolean isRead = false;

    /**
     * 讀取時間
     */
    @Column(name = "read_at")
    private LocalDateTime readAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 通知類型
     */
    public enum NotificationType {
        INFO("資訊"),
        SUCCESS("成功"),
        WARNING("警告"),
        ERROR("錯誤"),
        RULE_TRIGGERED("規則觸發");

        private final String displayName;

        NotificationType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
