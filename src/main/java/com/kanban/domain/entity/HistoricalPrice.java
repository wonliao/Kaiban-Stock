package com.kanban.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 歷史價格資料實體
 */
@Entity
@Table(name = "historical_prices",
       indexes = {
           @Index(name = "idx_historical_stock_date", columnList = "stock_code, trade_date DESC"),
           @Index(name = "idx_historical_date", columnList = "trade_date DESC")
       },
       uniqueConstraints = @UniqueConstraint(columnNames = {"stock_code", "trade_date"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoricalPrice {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(name = "stock_code", nullable = false, length = 10)
    private String stockCode;
    
    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;
    
    @Column(name = "open_price", precision = 10, scale = 2)
    private BigDecimal openPrice;
    
    @Column(name = "high_price", precision = 10, scale = 2, nullable = false)
    private BigDecimal highPrice;
    
    @Column(name = "low_price", precision = 10, scale = 2, nullable = false)
    private BigDecimal lowPrice;
    
    @Column(name = "close_price", precision = 10, scale = 2, nullable = false)
    private BigDecimal closePrice;
    
    @Column(name = "volume", nullable = false)
    private Long volume;
    
    @Column(name = "adjusted_close", precision = 10, scale = 2)
    private BigDecimal adjustedClose;
    
    @Column(name = "data_source", length = 50)
    @Builder.Default
    private String dataSource = "TWSE-MCP";
}