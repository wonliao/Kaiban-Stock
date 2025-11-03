package com.kanban.service;

import com.kanban.domain.entity.StockSnapshot;
import com.kanban.dto.twse.TwseStockData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 股票資料快取服務
 * 實作多層 TTL 策略與盤中/盤後不同更新頻率
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockCacheService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    
    // 交易時段定義
    private static final LocalTime MARKET_OPEN = LocalTime.of(9, 0);
    private static final LocalTime MARKET_CLOSE = LocalTime.of(13, 30);
    
    /**
     * 取得股票快照（根據交易時段使用不同快取策略）
     */
    public StockSnapshot getCachedStockSnapshot(String stockCode) {
        String cacheKey = getCacheKey("snapshot", stockCode);
        StockSnapshot snapshot = (StockSnapshot) redisTemplate.opsForValue().get(cacheKey);
        
        if (snapshot != null) {
            log.debug("Cache hit for stock snapshot: {}", stockCode);
            
            // 檢查資料是否過時
            if (isDataStale(snapshot)) {
                log.debug("Cached data is stale for {}, removing from cache", stockCode);
                redisTemplate.delete(cacheKey);
                return null;
            }
        } else {
            log.debug("Cache miss for stock snapshot: {}", stockCode);
        }
        
        return snapshot;
    }
    
    /**
     * 快取股票快照
     */
    public void cacheStockSnapshot(StockSnapshot snapshot) {
        if (snapshot == null || snapshot.getCode() == null) {
            return;
        }
        
        String cacheKey = getCacheKey("snapshot", snapshot.getCode());
        Duration ttl = getTtlForMarketHours();
        
        redisTemplate.opsForValue().set(cacheKey, snapshot, ttl.toSeconds(), TimeUnit.SECONDS);
        log.debug("Cached stock snapshot for {} with TTL: {}", snapshot.getCode(), ttl);
    }
    
    /**
     * 批次快取股票快照
     */
    public void cacheBatchStockSnapshots(List<StockSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return;
        }
        
        Duration ttl = getTtlForMarketHours();
        
        snapshots.forEach(snapshot -> {
            if (snapshot != null && snapshot.getCode() != null) {
                String cacheKey = getCacheKey("snapshot", snapshot.getCode());
                redisTemplate.opsForValue().set(cacheKey, snapshot, ttl.toSeconds(), TimeUnit.SECONDS);
            }
        });
        
        log.debug("Cached {} stock snapshots with TTL: {}", snapshots.size(), ttl);
    }
    
    /**
     * 快取股票驗證結果
     */
    @Cacheable(value = "stock-validation", key = "#stockCode")
    public Boolean cacheStockValidation(String stockCode, Boolean isValid) {
        log.debug("Caching stock validation for {}: {}", stockCode, isValid);
        return isValid;
    }
    
    /**
     * 取得快取的股票驗證結果
     */
    public Boolean getCachedStockValidation(String stockCode) {
        String cacheKey = getCacheKey("validation", stockCode);
        Boolean isValid = (Boolean) redisTemplate.opsForValue().get(cacheKey);
        
        if (isValid != null) {
            log.debug("Cache hit for stock validation: {} = {}", stockCode, isValid);
        } else {
            log.debug("Cache miss for stock validation: {}", stockCode);
        }
        
        return isValid;
    }
    
    /**
     * 快取歷史資料
     */
    public void cacheHistoricalData(String stockCode, String period, Object data) {
        String cacheKey = getCacheKey("historical", stockCode, period);
        Duration ttl = Duration.ofHours(24); // 歷史資料快取 24 小時
        
        redisTemplate.opsForValue().set(cacheKey, data, ttl.toSeconds(), TimeUnit.SECONDS);
        log.debug("Cached historical data for {} period {}", stockCode, period);
    }
    
    /**
     * 取得快取的歷史資料
     */
    public Object getCachedHistoricalData(String stockCode, String period) {
        String cacheKey = getCacheKey("historical", stockCode, period);
        Object data = redisTemplate.opsForValue().get(cacheKey);
        
        if (data != null) {
            log.debug("Cache hit for historical data: {} period {}", stockCode, period);
        } else {
            log.debug("Cache miss for historical data: {} period {}", stockCode, period);
        }
        
        return data;
    }
    
    /**
     * 清除特定股票的所有快取
     */
    @CacheEvict(value = {"stock-snapshot", "stock-snapshot-afterhours", "technical-indicators"}, key = "#stockCode")
    public void evictStockCache(String stockCode) {
        // 清除所有相關的快取鍵
        String pattern = "*:" + stockCode + ":*";
        redisTemplate.delete(redisTemplate.keys(pattern));
        log.debug("Evicted all cache for stock: {}", stockCode);
    }
    
    /**
     * 清除所有股票快照快取
     */
    @CacheEvict(value = {"stock-snapshot", "stock-snapshot-afterhours"}, allEntries = true)
    public void evictAllStockSnapshots() {
        log.debug("Evicted all stock snapshot cache");
    }
    
    /**
     * 檢查資料是否過時
     */
    private boolean isDataStale(StockSnapshot snapshot) {
        if (snapshot.getUpdatedAt() == null) {
            return true;
        }
        
        LocalDateTime now = LocalDateTime.now();
        Duration age = Duration.between(snapshot.getUpdatedAt(), now);
        Duration maxAge = getTtlForMarketHours();
        
        return age.compareTo(maxAge) > 0;
    }
    
    /**
     * 根據交易時段決定 TTL
     */
    private Duration getTtlForMarketHours() {
        LocalTime now = LocalTime.now();
        
        // 檢查是否為交易時段（週一至週五 09:00-13:30）
        if (isMarketHours(now) && isWeekday()) {
            return Duration.ofSeconds(60); // 盤中 60 秒
        } else {
            return Duration.ofSeconds(300); // 盤後 300 秒
        }
    }
    
    /**
     * 檢查是否為交易時段
     */
    private boolean isMarketHours(LocalTime time) {
        return !time.isBefore(MARKET_OPEN) && !time.isAfter(MARKET_CLOSE);
    }
    
    /**
     * 檢查是否為工作日
     */
    private boolean isWeekday() {
        LocalDateTime now = LocalDateTime.now();
        int dayOfWeek = now.getDayOfWeek().getValue();
        return dayOfWeek >= 1 && dayOfWeek <= 5; // 週一到週五
    }
    
    /**
     * 生成快取鍵
     */
    private String getCacheKey(String type, String... parts) {
        return "kanban:" + type + ":" + String.join(":", parts);
    }
    
    /**
     * 取得快取統計資訊
     */
    public CacheStats getCacheStats() {
        // 計算快取命中率等統計資訊
        long totalKeys = redisTemplate.keys("kanban:*").size();
        
        return CacheStats.builder()
                .totalKeys(totalKeys)
                .marketHours(isMarketHours(LocalTime.now()) && isWeekday())
                .currentTtl(getTtlForMarketHours())
                .build();
    }
    
    /**
     * 快取統計資訊
     */
    public static class CacheStats {
        private final long totalKeys;
        private final boolean marketHours;
        private final Duration currentTtl;
        
        private CacheStats(long totalKeys, boolean marketHours, Duration currentTtl) {
            this.totalKeys = totalKeys;
            this.marketHours = marketHours;
            this.currentTtl = currentTtl;
        }
        
        public static CacheStatsBuilder builder() {
            return new CacheStatsBuilder();
        }
        
        public long getTotalKeys() { return totalKeys; }
        public boolean isMarketHours() { return marketHours; }
        public Duration getCurrentTtl() { return currentTtl; }
        
        public static class CacheStatsBuilder {
            private long totalKeys;
            private boolean marketHours;
            private Duration currentTtl;
            
            public CacheStatsBuilder totalKeys(long totalKeys) {
                this.totalKeys = totalKeys;
                return this;
            }
            
            public CacheStatsBuilder marketHours(boolean marketHours) {
                this.marketHours = marketHours;
                return this;
            }
            
            public CacheStatsBuilder currentTtl(Duration currentTtl) {
                this.currentTtl = currentTtl;
                return this;
            }
            
            public CacheStats build() {
                return new CacheStats(totalKeys, marketHours, currentTtl);
            }
        }
    }
}