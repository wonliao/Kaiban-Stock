package com.kanban.dto.twse;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * TWSE-MCP API 回應的股票資料 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TwseStockData {
    
    @JsonProperty("Code")
    private String code;
    
    @JsonProperty("Name")
    private String name;
    
    @JsonProperty("ClosingPrice")
    private BigDecimal closingPrice;
    
    @JsonProperty("Change")
    private BigDecimal change;
    
    @JsonProperty("ChangePercent")
    private BigDecimal changePercent;
    
    @JsonProperty("OpeningPrice")
    private BigDecimal openingPrice;
    
    @JsonProperty("HighestPrice")
    private BigDecimal highestPrice;
    
    @JsonProperty("LowestPrice")
    private BigDecimal lowestPrice;
    
    @JsonProperty("TradeVolume")
    private Long tradeVolume;
    
    @JsonProperty("TradeValue")
    private BigDecimal tradeValue;
    
    @JsonProperty("Transaction")
    private Long transaction;
    
    @JsonProperty("TradeDate")
    private String tradeDate;
    
    @JsonProperty("TradeTime")
    private String tradeTime;
    
    // 計算屬性
    public BigDecimal getPreviousPrice() {
        if (closingPrice != null && change != null) {
            return closingPrice.subtract(change);
        }
        return null;
    }
    
    public BigDecimal getVolumeRatio() {
        // 簡化版本，實際需要歷史平均成交量
        return BigDecimal.valueOf(1.0);
    }
}