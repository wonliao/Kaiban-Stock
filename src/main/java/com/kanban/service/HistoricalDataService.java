package com.kanban.service;

import com.kanban.domain.entity.HistoricalPrice;
import com.kanban.domain.entity.StockSnapshot;
import com.kanban.dto.twse.TwseStockData;
import com.kanban.repository.HistoricalPriceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 歷史資料管理服務
 * 負責歷史價格資料的儲存與查詢
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HistoricalDataService {
    
    private final HistoricalPriceRepository historicalPriceRepository;
    private final StockCacheService stockCacheService;
    
    /**
     * 儲存股票的歷史價格資料
     */
    @Transactional
    public void saveHistoricalPrice(String stockCode, TwseStockData twseData) {
        if (twseData == null || stockCode == null) {
            return;
        }
        
        LocalDate tradeDate = LocalDate.now(); // 簡化版本，實際應從 twseData 解析
        
        // 檢查是否已存在
        if (historicalPriceRepository.existsByStockCodeAndTradeDate(stockCode, tradeDate)) {
            log.debug("Historical price already exists for {} on {}", stockCode, tradeDate);
            return;
        }
        
        HistoricalPrice historicalPrice = HistoricalPrice.builder()
                .stockCode(stockCode)
                .tradeDate(tradeDate)
                .openPrice(twseData.getOpeningPrice())
                .highPrice(twseData.getHighestPrice())
                .lowPrice(twseData.getLowestPrice())
                .closePrice(twseData.getClosingPrice())
                .volume(twseData.getTradeVolume())
                .adjustedClose(twseData.getClosingPrice()) // 簡化版本，未考慮除權息
                .dataSource("TWSE-MCP")
                .build();
        
        historicalPriceRepository.save(historicalPrice);
        log.debug("Saved historical price for {} on {}", stockCode, tradeDate);
    }
    
    /**
     * 從股票快照儲存歷史資料
     */
    @Transactional
    public void saveHistoricalPriceFromSnapshot(StockSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        
        LocalDate tradeDate = LocalDate.now();
        
        // 檢查是否已存在
        if (historicalPriceRepository.existsByStockCodeAndTradeDate(snapshot.getCode(), tradeDate)) {
            return;
        }
        
        HistoricalPrice historicalPrice = HistoricalPrice.builder()
                .stockCode(snapshot.getCode())
                .tradeDate(tradeDate)
                .openPrice(snapshot.getOpenPrice())
                .highPrice(snapshot.getHighPrice())
                .lowPrice(snapshot.getLowPrice())
                .closePrice(snapshot.getCurrentPrice())
                .volume(snapshot.getVolume())
                .adjustedClose(snapshot.getCurrentPrice())
                .dataSource(snapshot.getDataSource())
                .build();
        
        historicalPriceRepository.save(historicalPrice);
        log.debug("Saved historical price from snapshot for {}", snapshot.getCode());
    }
    
    /**
     * 批次儲存歷史資料
     */
    @Async
    @Transactional
    public CompletableFuture<Void> saveBatchHistoricalPrices(List<StockSnapshot> snapshots) {
        log.info("Saving batch historical prices for {} stocks", snapshots.size());
        
        snapshots.forEach(this::saveHistoricalPriceFromSnapshot);
        
        log.info("Completed saving batch historical prices");
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * 取得股票的歷史價格資料
     */
    public List<HistoricalPrice> getHistoricalPrices(String stockCode, int days) {
        return historicalPriceRepository.findRecentByStockCode(stockCode, days);
    }
    
    /**
     * 取得股票在指定日期範圍的歷史價格
     */
    public List<HistoricalPrice> getHistoricalPrices(String stockCode, LocalDate startDate, LocalDate endDate) {
        // 先檢查快取
        String cacheKey = stockCode + ":" + startDate + ":" + endDate;
        Object cachedData = stockCacheService.getCachedHistoricalData(stockCode, cacheKey);
        
        if (cachedData instanceof List<?>) {
            @SuppressWarnings("unchecked")
            List<HistoricalPrice> cached = (List<HistoricalPrice>) cachedData;
            return cached;
        }
        
        // 從資料庫查詢
        List<HistoricalPrice> prices = historicalPriceRepository
                .findByStockCodeAndDateRange(stockCode, startDate, endDate);
        
        // 快取結果
        stockCacheService.cacheHistoricalData(stockCode, cacheKey, prices);
        
        return prices;
    }
    
    /**
     * 清理舊的歷史資料
     */
    @Transactional
    public void cleanupOldData(int daysToKeep) {
        LocalDate cutoffDate = LocalDate.now().minusDays(daysToKeep);
        
        log.info("Cleaning up historical data older than {}", cutoffDate);
        historicalPriceRepository.deleteByTradeDateBefore(cutoffDate);
        log.info("Completed cleanup of historical data");
    }
    
    /**
     * 取得所有有歷史資料的股票代碼
     */
    public List<String> getAllStockCodesWithData() {
        return historicalPriceRepository.findAllStockCodes();
    }
    
    /**
     * 檢查股票是否有足夠的歷史資料進行技術分析
     */
    public boolean hasSufficientData(String stockCode, int requiredDays) {
        List<HistoricalPrice> prices = historicalPriceRepository
                .findRecentByStockCode(stockCode, requiredDays);
        
        return prices.size() >= requiredDays;
    }
}