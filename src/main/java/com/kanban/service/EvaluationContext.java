package com.kanban.service;

import com.kanban.domain.entity.Card;
import lombok.Data;

import java.math.BigDecimal;

/**
 * SpEL 評估上下文 - 提供所有可用的變數給規則條件評估
 */
@Data
public class EvaluationContext {

    // 卡片資訊
    private String stockCode;
    private String stockName;
    private String cardStatus;

    // 價格資訊
    private BigDecimal price;
    private BigDecimal currentPrice;
    private BigDecimal openPrice;
    private BigDecimal highPrice;
    private BigDecimal lowPrice;
    private BigDecimal previousClose;
    private BigDecimal change;
    private BigDecimal changePercent;

    // 成交量資訊
    private Long volume;
    private Long avgVolume;

    // 技術指標 - 移動平均線
    private BigDecimal ma5;
    private BigDecimal ma10;
    private BigDecimal ma20;
    private BigDecimal ma60;
    private BigDecimal ma5_ma20_diff;

    // 技術指標 - RSI
    private BigDecimal rsi;
    private BigDecimal rsi14;

    // 技術指標 - MACD
    private BigDecimal macd;
    private BigDecimal macdLine;
    private BigDecimal macdSignal;
    private BigDecimal macdHistogram;
    private Boolean macd_positive;
    private Boolean macd_signal_positive;

    // 技術指標 - KD
    private BigDecimal kValue;
    private BigDecimal kdK;
    private BigDecimal dValue;
    private BigDecimal kdD;

    // 技術指標 - 其他
    private BigDecimal volumeRatio;

    // 輔助常數
    private final Boolean TRUE = true;
    private final Boolean FALSE = false;
}
