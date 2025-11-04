package com.kanban.service;

import com.kanban.domain.entity.StockSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockCacheServiceTest {
    
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    
    @Mock
    private ValueOperations<String, Object> valueOperations;
    
    private StockCacheService stockCacheService;
    
    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        stockCacheService = new StockCacheService(redisTemplate);
    }
    
    @Test
    void getCachedStockSnapshot_CacheHit_ReturnsSnapshot() {
        // Arrange
        String stockCode = "2330";
        StockSnapshot expectedSnapshot = createTestSnapshot(stockCode);
        when(valueOperations.get("kanban:snapshot:" + stockCode)).thenReturn(expectedSnapshot);
        
        // Act
        StockSnapshot result = stockCacheService.getCachedStockSnapshot(stockCode);
        
        // Assert
        assertNotNull(result);
        assertEquals(stockCode, result.getCode());
        assertEquals("台積電", result.getName());
        verify(valueOperations).get("kanban:snapshot:" + stockCode);
    }
    
    @Test
    void getCachedStockSnapshot_CacheMiss_ReturnsNull() {
        // Arrange
        String stockCode = "2330";
        when(valueOperations.get("kanban:snapshot:" + stockCode)).thenReturn(null);
        
        // Act
        StockSnapshot result = stockCacheService.getCachedStockSnapshot(stockCode);
        
        // Assert
        assertNull(result);
        verify(valueOperations).get("kanban:snapshot:" + stockCode);
    }
    
    @Test
    void getCachedStockSnapshot_StaleData_RemovesFromCache() {
        // Arrange
        String stockCode = "2330";
        StockSnapshot staleSnapshot = createTestSnapshot(stockCode);
        staleSnapshot.setUpdatedAt(LocalDateTime.now().minusMinutes(10)); // 10 minutes old
        
        when(valueOperations.get("kanban:snapshot:" + stockCode)).thenReturn(staleSnapshot);
        
        // Act
        StockSnapshot result = stockCacheService.getCachedStockSnapshot(stockCode);
        
        // Assert
        assertNull(result);
        verify(redisTemplate).delete("kanban:snapshot:" + stockCode);
    }
    
    @Test
    void cacheStockSnapshot_ValidSnapshot_CachesWithCorrectTtl() {
        // Arrange
        StockSnapshot snapshot = createTestSnapshot("2330");
        
        // Act
        stockCacheService.cacheStockSnapshot(snapshot);
        
        // Assert
        verify(valueOperations).set(
            eq("kanban:snapshot:2330"), 
            eq(snapshot), 
            anyLong(), 
            eq(TimeUnit.SECONDS)
        );
    }
    
    @Test
    void cacheStockSnapshot_NullSnapshot_DoesNotCache() {
        // Act
        stockCacheService.cacheStockSnapshot(null);
        
        // Assert
        verify(valueOperations, never()).set(anyString(), any(), anyLong(), any(TimeUnit.class));
    }
    
    @Test
    void cacheBatchStockSnapshots_ValidSnapshots_CachesAll() {
        // Arrange
        List<StockSnapshot> snapshots = List.of(
            createTestSnapshot("2330"),
            createTestSnapshot("2317")
        );
        
        // Act
        stockCacheService.cacheBatchStockSnapshots(snapshots);
        
        // Assert
        verify(valueOperations, times(2)).set(
            anyString(), 
            any(StockSnapshot.class), 
            anyLong(), 
            eq(TimeUnit.SECONDS)
        );
    }
    
    @Test
    void getCachedStockValidation_CacheHit_ReturnsBoolean() {
        // Arrange
        String stockCode = "2330";
        when(valueOperations.get("kanban:validation:" + stockCode)).thenReturn(true);
        
        // Act
        Boolean result = stockCacheService.getCachedStockValidation(stockCode);
        
        // Assert
        assertTrue(result);
        verify(valueOperations).get("kanban:validation:" + stockCode);
    }
    
    @Test
    void cacheHistoricalData_ValidData_CachesWithLongTtl() {
        // Arrange
        String stockCode = "2330";
        String period = "1M";
        Object data = new Object();
        
        // Act
        stockCacheService.cacheHistoricalData(stockCode, period, data);
        
        // Assert
        verify(valueOperations).set(
            eq("kanban:historical:2330:1M"), 
            eq(data), 
            eq(86400L), // 24 hours in seconds
            eq(TimeUnit.SECONDS)
        );
    }
    
    @Test
    void evictStockCache_ValidStockCode_DeletesRelatedKeys() {
        // Arrange
        String stockCode = "2330";
        Set<String> keys = Set.of("kanban:snapshot:2330", "kanban:validation:2330");
        when(redisTemplate.keys("*:2330:*")).thenReturn(keys);
        
        // Act
        stockCacheService.evictStockCache(stockCode);
        
        // Assert
        verify(redisTemplate).delete(keys);
    }
    
    @Test
    void getCacheStats_ReturnsValidStats() {
        // Arrange
        Set<String> keys = Set.of("kanban:snapshot:2330", "kanban:validation:2317");
        when(redisTemplate.keys("kanban:*")).thenReturn(keys);
        
        // Act
        StockCacheService.CacheStats stats = stockCacheService.getCacheStats();
        
        // Assert
        assertNotNull(stats);
        assertEquals(2, stats.getTotalKeys());
        assertNotNull(stats.getCurrentTtl());
    }
    
    private StockSnapshot createTestSnapshot(String stockCode) {
        return StockSnapshot.builder()
                .code(stockCode)
                .name("台積電")
                .currentPrice(new BigDecimal("580.00"))
                .changePercent(new BigDecimal("2.5"))
                .volume(25000000L)
                .updatedAt(LocalDateTime.now())
                .dataSource("TWSE-MCP")
                .delayMinutes(15)
                .build();
    }
}