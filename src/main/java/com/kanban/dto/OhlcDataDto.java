package com.kanban.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * OHLC (Open, High, Low, Close) 資料傳輸物件
 * 用於圖表顯示的標準金融資料格式
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OhlcDataDto {

    /**
     * 交易日期
     */
    private LocalDate date;

    /**
     * 開盤價
     */
    private BigDecimal open;

    /**
     * 最高價
     */
    private BigDecimal high;

    /**
     * 最低價
     */
    private BigDecimal low;

    /**
     * 收盤價
     */
    private BigDecimal close;

    /**
     * 成交量
     */
    private Long volume;
}
