package com.kanban.dto;

import com.kanban.domain.entity.Card;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class CardDto {
    
    private String id;
    private String stockCode;
    private String stockName;
    private Card.CardStatus status;
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Stock data from snapshot
    private BigDecimal currentPrice;
    private BigDecimal changePercent;
    private Long volume;
    private BigDecimal ma20;
    private BigDecimal rsi;
    private LocalDateTime dataUpdatedAt;
    private String dataSource;
    private Integer delayMinutes;
}