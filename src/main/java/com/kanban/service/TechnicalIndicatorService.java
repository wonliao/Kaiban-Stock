package com.kanban.service;

import com.kanban.domain.entity.HistoricalPrice;
import com.kanban.domain.entity.StockSnapshot;
import com.kanban.domain.entity.TechnicalIndicator;
import com.kanban.repository.HistoricalPriceRepository;
import com.kanban.repository.TechnicalIndicatorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 技術指標計算服務
 * 實作 MA、RSI、KD 指標計算邏輯
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TechnicalIndicatorService {
    
    private final TechnicalIndicatorRepository technicalIndicatorRepository;
    private final HistoricalPriceRepository historicalPriceRepository;
    private final StockCacheService stockCacheService;
    
    /**
     * 計算並更新股票的技術指標
     */
    @Transactional
    public TechnicalIndicator calculateIndicators(String stockCode) {
        log.debug("Calculating technical indicators for stock: {}", stockCode);
        
        // 取得歷史價格資料
        List<HistoricalPrice> historicalPrices = historicalPriceRepository
                .findRecentByStockCode(stockCode, 100); // 取最近 100 天資料
        
        if (historicalPrices.size() < 20) {
            log.warn("Insufficient historical data for {}: {} days", stockCode, historicalPrices.size());
            return createInsufficientDataIndicator(stockCode);
        }
        
        // 計算各項技術指標
        TechnicalIndicator indicator = TechnicalIndicator.builder()
                .stockCode(stockCode)
                .calculationDate(LocalDateTime.now())
                .dataPointsCount(historicalPrices.size())
                .build();
        
        // 計算移動平均線
        calculateMovingAverages(indicator, historicalPrices);
        
        // 計算 RSI
        calculateRSI(indicator, historicalPrices);
        
        // 計算 KD 指標
        calculateKD(indicator, historicalPrices);
        
        // 計算 MACD
        calculateMACD(indicator, historicalPrices);
        
        // 計算成交量指標
        calculateVolumeIndicators(indicator, historicalPrices);
        
        // 儲存到資料庫
        TechnicalIndicator savedIndicator = technicalIndicatorRepository.save(indicator);
        
        // 更新股票快照中的技術指標
        updateStockSnapshotIndicators(stockCode, savedIndicator);
        
        log.debug("Successfully calculated indicators for {}", stockCode);
        return savedIndicator;
    }
    
    /**
     * 批次計算多檔股票的技術指標
     */
    @Async
    public CompletableFuture<Void> calculateBatchIndicators(List<String> stockCodes) {
        log.info("Starting batch calculation for {} stocks", stockCodes.size());
        
        stockCodes.parallelStream().forEach(stockCode -> {
            try {
                calculateIndicators(stockCode);
            } catch (Exception e) {
                log.error("Error calculating indicators for {}: {}", stockCode, e.getMessage());
            }
        });
        
        log.info("Completed batch calculation for {} stocks", stockCodes.size());
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * 取得股票的最新技術指標（優先從快取）
     */
    @Cacheable(value = "technical-indicators", key = "#stockCode")
    public TechnicalIndicator getLatestIndicators(String stockCode) {
        return technicalIndicatorRepository.findLatestByStockCode(stockCode)
                .orElse(null);
    }
    
    /**
     * 計算移動平均線 (MA5, MA10, MA20, MA60)
     */
    private void calculateMovingAverages(TechnicalIndicator indicator, List<HistoricalPrice> prices) {
        if (prices.size() >= 5) {
            indicator.setMa5(calculateSMA(prices, 5));
        }
        if (prices.size() >= 10) {
            indicator.setMa10(calculateSMA(prices, 10));
        }
        if (prices.size() >= 20) {
            indicator.setMa20(calculateSMA(prices, 20));
        }
        if (prices.size() >= 60) {
            indicator.setMa60(calculateSMA(prices, 60));
        }
    }
    
    /**
     * 計算簡單移動平均線
     */
    private BigDecimal calculateSMA(List<HistoricalPrice> prices, int period) {
        if (prices.size() < period) {
            return null;
        }
        
        BigDecimal sum = prices.stream()
                .limit(period)
                .map(HistoricalPrice::getClosePrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return sum.divide(BigDecimal.valueOf(period), 2, RoundingMode.HALF_UP);
    }
    
    /**
     * 計算 RSI (14 日期間)
     */
    private void calculateRSI(TechnicalIndicator indicator, List<HistoricalPrice> prices) {
        if (prices.size() < 15) { // 需要至少 15 天資料 (14 + 1)
            return;
        }
        
        BigDecimal avgGain = BigDecimal.ZERO;
        BigDecimal avgLoss = BigDecimal.ZERO;
        
        // 計算前 14 天的平均漲跌幅
        for (int i = 1; i < 15; i++) {
            BigDecimal change = prices.get(i-1).getClosePrice().subtract(prices.get(i).getClosePrice());
            if (change.compareTo(BigDecimal.ZERO) > 0) {
                avgGain = avgGain.add(change);
            } else {
                avgLoss = avgLoss.add(change.abs());
            }
        }
        
        avgGain = avgGain.divide(BigDecimal.valueOf(14), 4, RoundingMode.HALF_UP);
        avgLoss = avgLoss.divide(BigDecimal.valueOf(14), 4, RoundingMode.HALF_UP);
        
        if (avgLoss.compareTo(BigDecimal.ZERO) == 0) {
            indicator.setRsi14(BigDecimal.valueOf(100));
            return;
        }
        
        BigDecimal rs = avgGain.divide(avgLoss, 4, RoundingMode.HALF_UP);
        BigDecimal rsi = BigDecimal.valueOf(100).subtract(
                BigDecimal.valueOf(100).divide(BigDecimal.ONE.add(rs), 2, RoundingMode.HALF_UP));
        
        indicator.setRsi14(rsi);
    }
    
    /**
     * 計算 KD 指標 (9 日期間)
     */
    private void calculateKD(TechnicalIndicator indicator, List<HistoricalPrice> prices) {
        if (prices.size() < 9) {
            return;
        }
        
        // 計算最近 9 天的最高價和最低價
        BigDecimal highest = prices.stream()
                .limit(9)
                .map(HistoricalPrice::getHighPrice)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        
        BigDecimal lowest = prices.stream()
                .limit(9)
                .map(HistoricalPrice::getLowPrice)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        
        BigDecimal currentClose = prices.get(0).getClosePrice();
        
        // 計算 RSV (Raw Stochastic Value)
        BigDecimal rsv;
        if (highest.equals(lowest)) {
            rsv = BigDecimal.valueOf(50); // 避免除零
        } else {
            rsv = currentClose.subtract(lowest)
                    .divide(highest.subtract(lowest), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }
        
        // 簡化版 K 值計算 (實際應該使用前一日的 K 值)
        BigDecimal kValue = rsv.multiply(BigDecimal.valueOf(0.33))
                .add(BigDecimal.valueOf(50).multiply(BigDecimal.valueOf(0.67)));
        
        // 簡化版 D 值計算 (實際應該使用前一日的 D 值)
        BigDecimal dValue = kValue.multiply(BigDecimal.valueOf(0.33))
                .add(BigDecimal.valueOf(50).multiply(BigDecimal.valueOf(0.67)));
        
        indicator.setKdK(kValue.setScale(2, RoundingMode.HALF_UP));
        indicator.setKdD(dValue.setScale(2, RoundingMode.HALF_UP));
    }
    
    /**
     * 計算 MACD 指標
     */
    private void calculateMACD(TechnicalIndicator indicator, List<HistoricalPrice> prices) {
        if (prices.size() < 26) {
            return;
        }
        
        // 計算 EMA12 和 EMA26
        BigDecimal ema12 = calculateEMA(prices, 12);
        BigDecimal ema26 = calculateEMA(prices, 26);
        
        if (ema12 != null && ema26 != null) {
            BigDecimal macdLine = ema12.subtract(ema26);
            indicator.setMacdLine(macdLine);
            
            // 簡化版 Signal 線計算 (實際應該是 MACD 線的 9 日 EMA)
            BigDecimal signal = macdLine.multiply(BigDecimal.valueOf(0.2))
                    .add(BigDecimal.ZERO.multiply(BigDecimal.valueOf(0.8)));
            indicator.setMacdSignal(signal);
            
            // MACD 柱狀圖
            BigDecimal histogram = macdLine.subtract(signal);
            indicator.setMacdHistogram(histogram);
        }
    }
    
    /**
     * 計算指數移動平均線 (EMA)
     */
    private BigDecimal calculateEMA(List<HistoricalPrice> prices, int period) {
        if (prices.size() < period) {
            return null;
        }
        
        BigDecimal multiplier = BigDecimal.valueOf(2.0 / (period + 1));
        BigDecimal ema = prices.get(period - 1).getClosePrice(); // 從 SMA 開始
        
        for (int i = period - 2; i >= 0; i--) {
            BigDecimal price = prices.get(i).getClosePrice();
            ema = price.multiply(multiplier).add(ema.multiply(BigDecimal.ONE.subtract(multiplier)));
        }
        
        return ema.setScale(4, RoundingMode.HALF_UP);
    }
    
    /**
     * 計算成交量指標
     */
    private void calculateVolumeIndicators(TechnicalIndicator indicator, List<HistoricalPrice> prices) {
        if (prices.size() >= 5) {
            Long volumeMa5 = prices.stream()
                    .limit(5)
                    .mapToLong(HistoricalPrice::getVolume)
                    .sum() / 5;
            indicator.setVolumeMa5(volumeMa5);
        }
        
        if (prices.size() >= 20) {
            Long volumeMa20 = prices.stream()
                    .limit(20)
                    .mapToLong(HistoricalPrice::getVolume)
                    .sum() / 20;
            indicator.setVolumeMa20(volumeMa20);
            
            // 計算量比
            Long currentVolume = prices.get(0).getVolume();
            if (volumeMa20 > 0) {
                BigDecimal volumeRatio = BigDecimal.valueOf(currentVolume)
                        .divide(BigDecimal.valueOf(volumeMa20), 2, RoundingMode.HALF_UP);
                indicator.setVolumeRatio(volumeRatio);
            }
        }
    }
    
    /**
     * 建立資料不足的指標物件
     */
    private TechnicalIndicator createInsufficientDataIndicator(String stockCode) {
        return TechnicalIndicator.builder()
                .stockCode(stockCode)
                .calculationDate(LocalDateTime.now())
                .dataPointsCount(0)
                .calculationSource("INSUFFICIENT_DATA")
                .build();
    }
    
    /**
     * 更新股票快照中的技術指標
     */
    private void updateStockSnapshotIndicators(String stockCode, TechnicalIndicator indicator) {
        try {
            // 清除快取，強制重新載入
            stockCacheService.evictStockCache(stockCode);
            
            log.debug("Updated stock snapshot indicators for {}", stockCode);
        } catch (Exception e) {
            log.error("Error updating stock snapshot indicators for {}: {}", stockCode, e.getMessage());
        }
    }
}