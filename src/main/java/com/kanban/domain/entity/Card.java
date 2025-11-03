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

@Entity
@Table(name = "cards", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "stock_code"}),
       indexes = {
           @Index(name = "idx_card_user_status", columnList = "user_id, status, updated_at"),
           @Index(name = "idx_card_stock_code", columnList = "stock_code")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Card {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "watchlist_id", nullable = false)
    private Watchlist watchlist;
    
    @Column(name = "stock_code", nullable = false, length = 10)
    private String stockCode;
    
    @Column(name = "stock_name", nullable = false)
    private String stockName;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private CardStatus status = CardStatus.WATCH;
    
    @Column(columnDefinition = "TEXT")
    private String note;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    public enum CardStatus {
        WATCH("觀察"),
        READY_TO_BUY("準備買進"),
        HOLD("持有"),
        SELL("賣出"),
        ALERTS("警示"),
        ARCHIVED("已封存");
        
        private final String displayName;
        
        CardStatus(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
}