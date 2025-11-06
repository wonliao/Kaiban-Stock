package com.kanban.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * 圖表資料傳輸物件
 * 包含股票代碼與對應的 OHLC 資料列表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChartDataDto {

    /**
     * 股票代碼
     */
    private String stockCode;

    /**
     * OHLC 資料列表
     */
    private List<OhlcDataDto> data;

    /**
     * 資料期間（如 "7d", "30d", "90d", "1y", "custom"）
     */
    private String period;

    /**
     * 開始日期（當 period 為 custom 時使用）
     */
    private LocalDate startDate;

    /**
     * 結束日期（當 period 為 custom 時使用）
     */
    private LocalDate endDate;
}
