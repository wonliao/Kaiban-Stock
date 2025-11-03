package com.kanban.service;

import com.kanban.domain.entity.StockSnapshot;
import com.kanban.dto.twse.TwseStockData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 股票資料轉換服務
 * 負責將 TWSE-MCP 資料轉換為內部資料模型
 */
@Slf4j
@Service
public class StockDataConverter {
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    
    /**
     * 將 TWSE 資料轉換為股票快照
     */
    public StockSnapshot convertToStockSnapshot(TwseStockData twseData) {
        if (twseData == null) {
            return null;
        }
        
        try {
            StockSnapshot snapshot = new StockSnapshot();
            snapshot.setCode(twseData.getCode());
            snapshot.setName(twseData.getName());
            snapshot.setCurrentPrice(twseData.getClosingPrice());
            snapshot.setChangePercent(twseData.getChangePercent());
            snapshot.setVolume(twseData.getTradeVolume());
            snapshot.setOpenPrice(twseData.getOpeningPrice());
            snapshot.setHighPrice(twseData.getHighestPrice());
            snapshot.setLowPrice(twseData.getLowestPrice());
            snapshot.setUpdatedAt(parseDateTime(twseData.getTradeDate(), twseData.getTradeTime()));
            snapshot.setDataSource("TWSE-MCP");
            snapshot.setDelayMinutes(15); // TWSE 資料延遲 15-20 分鐘
            
            // 計算前一日收盤價
            if (twseData.getClosingPrice() != null && twseData.getChange() != null) {
                snapshot.setPreviousClose(twseData.getClosingPrice().subtract(twseData.getChange()));
            }
            
            log.debug("Converted TWSE data to snapshot for {}: {}", twseData.getCode(), snapshot);
            return snapshot;
            
        } catch (Exception e) {
            log.error("Error converting TWSE data to snapshot for {}: {}", twseData.getCode(), e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 解析日期時間
     */
    private LocalDateTime parseDateTime(String dateStr, String timeStr) {
        try {
            if (dateStr != null && timeStr != null) {
                return LocalDateTime.parse(dateStr + "T" + timeStr);
            }
        } catch (DateTimeParseException e) {
            log.warn("Failed to parse date/time: {} {}, using current time", dateStr, timeStr);
        }
        
        return LocalDateTime.now();
    }
    
    /**
     * 驗證股票資料完整性
     */
    public boolean isValidStockData(TwseStockData twseData) {
        if (twseData == null) {
            return false;
        }
        
        // 檢查必要欄位
        if (twseData.getCode() == null || twseData.getCode().trim().isEmpty()) {
            log.warn("Invalid stock data: missing code");
            return false;
        }
        
        if (twseData.getName() == null || twseData.getName().trim().isEmpty()) {
            log.warn("Invalid stock data: missing name for {}", twseData.getCode());
            return false;
        }
        
        if (twseData.getClosingPrice() == null || twseData.getClosingPrice().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            log.warn("Invalid stock data: invalid closing price for {}", twseData.getCode());
            return false;
        }
        
        return true;
    }
    
    /**
     * 計算量比 (Volume Ratio)
     * 簡化版本，實際需要歷史平均成交量
     */
    public java.math.BigDecimal calculateVolumeRatio(TwseStockData twseData, Long averageVolume) {
        if (twseData.getTradeVolume() == null || averageVolume == null || averageVolume == 0) {
            return java.math.BigDecimal.ONE;
        }
        
        return java.math.BigDecimal.valueOf(twseData.getTradeVolume())
                .divide(java.math.BigDecimal.valueOf(averageVolume), 2, java.math.RoundingMode.HALF_UP);
    }
}