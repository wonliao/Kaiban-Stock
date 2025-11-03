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

/**
 * 技術指標實體
 */
@Entity
@Table(name = "technical_indicators",
       indexes = {
           @Index(name = "idx_technical_stock_date", columnList = "stock_code, calculation_date DESC"),
           @Index(name = "idx_technical_updated", columnList = "updated_at DESC")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class TechnicalIndicator {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(name = "stock_code", nullable = false, length = 10)
    private String stockCode;
    
    @Column(name = "calculation_date", nullable = false)
    private LocalDateTime calculationDate;
    
    // 移動平均線
    @Column(name = "ma5", precision = 10, scale = 2)
    private BigDecimal ma5;
    
    @Column(name = "ma10", precision = 10, scale = 2)
    private BigDecimal ma10;
    
    @Column(name = "ma20", precision = 10, scale = 2)
    private BigDecimal ma20;
    
    @Column(name = "ma60", precision = 10, scale = 2)
    private BigDecimal ma60;
    
    // RSI 指標
    @Column(name = "rsi_14", precision = 5, scale = 2)
    private BigDecimal rsi14;
    
    // KD 指標
    @Column(name = "kd_k", precision = 5, scale = 2)
    private BigDecimal kdK;
    
    @Column(name = "kd_d", precision = 5, scale = 2)
    private BigDecimal kdD;
    
    // MACD 指標
    @Column(name = "macd_line", precision = 10, scale = 4)
    private BigDecimal macdLine;
    
    @Column(name = "macd_signal", precision = 10, scale = 4)
    private BigDecimal macdSignal;
    
    @Column(name = "macd_histogram", precision = 10, scale = 4)
    private BigDecimal macdHistogram;
    
    // 成交量相關指標
    @Column(name = "volume_ma5")
    private Long volumeMa5;
    
    @Column(name = "volume_ma20")
    private Long volumeMa20;
    
    @Column(name = "volume_ratio", precision = 5, scale = 2)
    private BigDecimal volumeRatio;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "data_points_count")
    private Integer dataPointsCount;
    
    @Column(name = "calculation_source", length = 50)
    @Builder.Default
    private String calculationSource = "INTERNAL";
}