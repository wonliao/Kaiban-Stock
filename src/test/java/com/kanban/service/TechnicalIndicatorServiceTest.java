package com.kanban.service;

import com.kanban.domain.entity.HistoricalPrice;
import com.kanban.domain.entity.TechnicalIndicator;
import com.kanban.repository.HistoricalPriceRepository;
import com.kanban.repository.TechnicalIndicatorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TechnicalIndicatorServiceTest {
    
    @Mock
    private TechnicalIndicatorRepository technicalIndicatorRepository;
    
    @Mock
    private HistoricalPriceRepository historicalPriceRepository;
    
    @Mock
    private StockCacheService stockCacheService;
    
    private TechnicalIndicatorService technicalIndicatorService;
    
    @BeforeEach
    void setUp() {
        technicalIndicatorService = new TechnicalIndicatorService(
                technicalIndicatorRepository,
                historicalPriceRepository,
                stockCacheService
        );
    }
    
    @Test
    void calculateIndicators_SufficientData_ReturnsCalculatedIndicators() {
        // Arrange
        String stockCode = "2330";
        List<HistoricalPrice> historicalPrices = createMockHistoricalPrices(stockCode, 30);
        
        when(historicalPriceRepository.findRecentByStockCode(stockCode, 100))
                .thenReturn(historicalPrices);
        when(technicalIndicatorRepository.save(any(TechnicalIndicator.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // Act
        TechnicalIndicator result = technicalIndicatorService.calculateIndicators(stockCode);
        
        // Assert
        assertNotNull(result);
        assertEquals(stockCode, result.getStockCode());
        assertEquals(30, result.getDataPointsCount());
        assertNotNull(result.getMa5());
        assertNotNull(result.getMa10());
        assertNotNull(result.getMa20());
        assertNotNull(result.getRsi14());
        
        verify(technicalIndicatorRepository).save(any(TechnicalIndicator.class));
        verify(stockCacheService).evictStockCache(stockCode);
    }
    
    @Test
    void calculateIndicators_InsufficientData_ReturnsInsufficientDataIndicator() {
        // Arrange
        String stockCode = "2330";
        List<HistoricalPrice> historicalPrices = createMockHistoricalPrices(stockCode, 10); // 少於 20 天

        when(historicalPriceRepository.findRecentByStockCode(stockCode, 100))
                .thenReturn(historicalPrices);

        // Act
        TechnicalIndicator result = technicalIndicatorService.calculateIndicators(stockCode);

        // Assert
        assertNotNull(result);
        assertEquals(stockCode, result.getStockCode());
        assertEquals("INSUFFICIENT_DATA", result.getCalculationSource());
        assertEquals(0, result.getDataPointsCount());

        // 資料不足時不應該儲存到資料庫
        verify(technicalIndicatorRepository, never()).save(any(TechnicalIndicator.class));
    }
    
    @Test
    void getLatestIndicators_ExistingData_ReturnsIndicator() {
        // Arrange
        String stockCode = "2330";
        TechnicalIndicator expectedIndicator = createMockTechnicalIndicator(stockCode);
        
        when(technicalIndicatorRepository.findLatestByStockCode(stockCode))
                .thenReturn(Optional.of(expectedIndicator));
        
        // Act
        TechnicalIndicator result = technicalIndicatorService.getLatestIndicators(stockCode);
        
        // Assert
        assertNotNull(result);
        assertEquals(stockCode, result.getStockCode());
        assertEquals(expectedIndicator.getMa20(), result.getMa20());
        
        verify(technicalIndicatorRepository).findLatestByStockCode(stockCode);
    }
    
    @Test
    void getLatestIndicators_NoData_ReturnsNull() {
        // Arrange
        String stockCode = "2330";
        
        when(technicalIndicatorRepository.findLatestByStockCode(stockCode))
                .thenReturn(Optional.empty());
        
        // Act
        TechnicalIndicator result = technicalIndicatorService.getLatestIndicators(stockCode);
        
        // Assert
        assertNull(result);
        
        verify(technicalIndicatorRepository).findLatestByStockCode(stockCode);
    }
    
    private List<HistoricalPrice> createMockHistoricalPrices(String stockCode, int days) {
        List<HistoricalPrice> prices = new ArrayList<>();
        LocalDate baseDate = LocalDate.now();
        
        for (int i = 0; i < days; i++) {
            BigDecimal basePrice = BigDecimal.valueOf(580.0 + (Math.random() - 0.5) * 20); // 570-590 範圍
            
            HistoricalPrice price = HistoricalPrice.builder()
                    .stockCode(stockCode)
                    .tradeDate(baseDate.minusDays(i))
                    .openPrice(basePrice.subtract(BigDecimal.valueOf(Math.random() * 5)))
                    .highPrice(basePrice.add(BigDecimal.valueOf(Math.random() * 10)))
                    .lowPrice(basePrice.subtract(BigDecimal.valueOf(Math.random() * 10)))
                    .closePrice(basePrice)
                    .volume((long) (20000000 + Math.random() * 10000000)) // 2000萬-3000萬
                    .build();
            
            prices.add(price);
        }
        
        return prices;
    }
    
    private TechnicalIndicator createMockTechnicalIndicator(String stockCode) {
        return TechnicalIndicator.builder()
                .stockCode(stockCode)
                .calculationDate(LocalDateTime.now())
                .ma5(BigDecimal.valueOf(575.50))
                .ma10(BigDecimal.valueOf(578.20))
                .ma20(BigDecimal.valueOf(580.10))
                .ma60(BigDecimal.valueOf(582.30))
                .rsi14(BigDecimal.valueOf(65.5))
                .kdK(BigDecimal.valueOf(72.3))
                .kdD(BigDecimal.valueOf(68.9))
                .dataPointsCount(30)
                .build();
    }
}