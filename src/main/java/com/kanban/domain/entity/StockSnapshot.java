package com.kanban.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_snapshots",
       indexes = @Index(name = "idx_stock_snapshot_updated", columnList = "updated_at DESC"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class StockSnapshot {
    
    @Id
    @Column(name = "stock_code", length = 10)
    private String code;
    
    @Column(nullable = false)
    private String name;
    
    @Column(name = "current_price", precision = 10, scale = 2)
    private BigDecimal currentPrice;
    
    @Column(name = "change_percent", precision = 5, scale = 2)
    private BigDecimal changePercent;
    
    @Column(name = "volume")
    private Long volume;
    
    @Column(name = "open_price", precision = 10, scale = 2)
    private BigDecimal openPrice;
    
    @Column(name = "high_price", precision = 10, scale = 2)
    private BigDecimal highPrice;
    
    @Column(name = "low_price", precision = 10, scale = 2)
    private BigDecimal lowPrice;
    
    @Column(name = "previous_close", precision = 10, scale = 2)
    private BigDecimal previousClose;
    
    @Column(name = "ma5", precision = 10, scale = 2)
    private BigDecimal ma5;
    
    @Column(name = "ma10", precision = 10, scale = 2)
    private BigDecimal ma10;
    
    @Column(name = "ma20", precision = 10, scale = 2)
    private BigDecimal ma20;
    
    @Column(name = "ma60", precision = 10, scale = 2)
    private BigDecimal ma60;
    
    @Column(name = "rsi", precision = 5, scale = 2)
    private BigDecimal rsi;
    
    @Column(name = "kd_k", precision = 5, scale = 2)
    private BigDecimal kdK;
    
    @Column(name = "kd_d", precision = 5, scale = 2)
    private BigDecimal kdD;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "data_source", length = 50)
    @Builder.Default
    private String dataSource = "TWSE-MCP";
    
    @Column(name = "delay_minutes")
    @Builder.Default
    private Integer delayMinutes = 15;
}