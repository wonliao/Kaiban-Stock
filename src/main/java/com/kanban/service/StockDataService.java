package com.kanban.service;

import com.kanban.client.MockTwseMcpClient;
import com.kanban.client.TwseMcpClient;
import com.kanban.domain.entity.StockSnapshot;
import com.kanban.dto.twse.TwseStockData;
import com.kanban.exception.StockNotFoundException;
import com.kanban.exception.TwseMcpException;
import com.kanban.repository.StockSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 股票資料服務
 * 整合 TWSE-MCP 客戶端、快取策略與資料轉換
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockDataService {
    
    private final TwseMcpClient twseMcpClient;
    private final MockTwseMcpClient mockTwseMcpClient;
    private final StockCacheService stockCacheService;
    private final StockDataConverter stockDataConverter;
    private final StockSnapshotRepository stockSnapshotRepository;
    private final TechnicalIndicatorService technicalIndicatorService;
    private final HistoricalDataService historicalDataService;
    private final InfluxDBService influxDBService;
    
    @Value("${twse.mcp.mock.enabled:false}")
    private boolean mockEnabled;
    
    /**
     * 取得股票快照（優先從快取取得）
     */
    public StockSnapshot getSnapshot(String stockCode) {
        log.debug("Getting snapshot for stock: {}", stockCode);
        
        // 1. 先從快取取得
        StockSnapshot cachedSnapshot = stockCacheService.getCachedStockSnapshot(stockCode);
        if (cachedSnapshot != null) {
            return cachedSnapshot;
        }
        
        // 2. 快取未命中，從 TWSE-MCP 取得
        try {
            CompletableFuture<TwseStockData> future = mockEnabled ? 
                mockTwseMcpClient.getStockData(stockCode) : 
                twseMcpClient.getStockData(stockCode);
            
            TwseStockData twseData = future.get();
            
            if (!stockDataConverter.isValidStockData(twseData)) {
                log.warn("Invalid stock data received for {}", stockCode);
                return getFallbackSnapshot(stockCode);
            }
            
            // 3. 轉換並快取
            StockSnapshot snapshot = stockDataConverter.convertToStockSnapshot(twseData);
            if (snapshot != null) {
                stockCacheService.cacheStockSnapshot(snapshot);
                
                // 4. 儲存到資料庫
                saveSnapshotToDatabase(snapshot);
                
                // 5. 儲存到 InfluxDB 時序資料庫
                influxDBService.writeStockSnapshot(snapshot);
                
                // 6. 儲存歷史資料並更新技術指標
                historicalDataService.saveHistoricalPriceFromSnapshot(snapshot);
                updateTechnicalIndicators(stockCode, snapshot);
            }
            
            return snapshot;
            
        } catch (Exception e) {
            log.error("Error fetching stock data for {}: {}", stockCode, e.getMessage());
            return getFallbackSnapshot(stockCode);
        }
    }
    
    /**
     * 批次更新所有股票快照
     */
    @Async
    public CompletableFuture<Void> updateAllSnapshots() {
        log.info("Starting batch update of all stock snapshots");
        
        try {
            // 取得所有需要更新的股票代碼
            List<String> stockCodes = getActiveStockCodes();
            
            if (stockCodes.isEmpty()) {
                log.info("No active stock codes found for update");
                return CompletableFuture.completedFuture(null);
            }
            
            // 批次取得資料
            CompletableFuture<List<TwseStockData>> future = mockEnabled ?
                mockTwseMcpClient.getBatchStockData(stockCodes) :
                twseMcpClient.getBatchStockData(stockCodes);
            
            List<TwseStockData> twseDataList = future.get();
            
            // 轉換並快取
            List<StockSnapshot> snapshots = twseDataList.stream()
                    .filter(stockDataConverter::isValidStockData)
                    .map(stockDataConverter::convertToStockSnapshot)
                    .filter(snapshot -> snapshot != null)
                    .toList();
            
            if (!snapshots.isEmpty()) {
                // 批次快取
                stockCacheService.cacheBatchStockSnapshots(snapshots);
                
                // 批次儲存到資料庫
                stockSnapshotRepository.saveAll(snapshots);
                
                // 批次儲存到 InfluxDB
                influxDBService.writeBatchStockSnapshots(snapshots);
                
                // 批次儲存歷史資料
                historicalDataService.saveBatchHistoricalPrices(snapshots);
                
                // 批次更新技術指標
                List<String> updatedStockCodes = snapshots.stream()
                        .map(StockSnapshot::getCode)
                        .toList();
                technicalIndicatorService.calculateBatchIndicators(updatedStockCodes);
                
                log.info("Successfully updated {} stock snapshots", snapshots.size());
            }
            
        } catch (Exception e) {
            log.error("Error during batch update: {}", e.getMessage(), e);
            throw new TwseMcpException("BATCH_UPDATE_FAILED", "批次更新失敗", 500, e);
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * 驗證股票代碼
     */
    public boolean validateStockCode(String stockCode) {
        log.debug("Validating stock code: {}", stockCode);
        
        // 1. 先從快取檢查
        Boolean cachedResult = stockCacheService.getCachedStockValidation(stockCode);
        if (cachedResult != null) {
            return cachedResult;
        }
        
        // 2. 快取未命中，呼叫 TWSE-MCP
        try {
            CompletableFuture<Boolean> future = mockEnabled ?
                mockTwseMcpClient.validateStockCode(stockCode) :
                twseMcpClient.validateStockCode(stockCode);
            
            Boolean isValid = future.get();
            
            // 3. 快取結果
            stockCacheService.cacheStockValidation(stockCode, isValid);
            
            return isValid;
            
        } catch (Exception e) {
            log.error("Error validating stock code {}: {}", stockCode, e.getMessage());
            return false;
        }
    }
    
    /**
     * 強制更新股票快照（清除快取後重新取得）
     */
    public StockSnapshot forceUpdateSnapshot(String stockCode) {
        log.debug("Force updating snapshot for stock: {}", stockCode);
        
        // 清除快取
        stockCacheService.evictStockCache(stockCode);
        
        // 重新取得
        return getSnapshot(stockCode);
    }
    
    /**
     * 取得快取統計資訊
     */
    public StockCacheService.CacheStats getCacheStats() {
        return stockCacheService.getCacheStats();
    }
    
    /**
     * 取得備援快照資料（從資料庫）
     */
    private StockSnapshot getFallbackSnapshot(String stockCode) {
        log.debug("Getting fallback snapshot for {}", stockCode);
        
        return stockSnapshotRepository.findById(stockCode)
                .map(snapshot -> {
                    // 標記為過時資料
                    snapshot.setDataSource("DATABASE_FALLBACK");
                    snapshot.setDelayMinutes(null); // 未知延遲
                    return snapshot;
                })
                .orElse(null);
    }
    
    /**
     * 儲存快照到資料庫
     */
    private void saveSnapshotToDatabase(StockSnapshot snapshot) {
        try {
            stockSnapshotRepository.save(snapshot);
            log.debug("Saved snapshot to database for {}", snapshot.getCode());
        } catch (Exception e) {
            log.error("Error saving snapshot to database for {}: {}", snapshot.getCode(), e.getMessage());
        }
    }
    
    /**
     * 更新技術指標
     */
    private void updateTechnicalIndicators(String stockCode, StockSnapshot snapshot) {
        try {
            // 檢查是否有足夠的歷史資料
            if (historicalDataService.hasSufficientData(stockCode, 20)) {
                technicalIndicatorService.calculateIndicators(stockCode);
            } else {
                log.debug("Insufficient historical data for technical indicators: {}", stockCode);
            }
        } catch (Exception e) {
            log.error("Error updating technical indicators for {}: {}", stockCode, e.getMessage());
        }
    }
    
    /**
     * 取得所有活躍的股票代碼
     */
    private List<String> getActiveStockCodes() {
        // 從觀察清單中取得所有股票代碼
        // 這裡簡化實作，實際應該從 Card 或 Watchlist 表查詢
        return List.of("2330", "2317", "2454", "2881", "2882", "2412", "2303", "1301", "1303", "2002");
    }
}