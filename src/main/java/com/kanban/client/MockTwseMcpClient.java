package com.kanban.client;

import com.kanban.dto.twse.TwseStockData;
import com.kanban.exception.StockNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

/**
 * TWSE-MCP Mock 客戶端
 * 用於開發測試與 TWSE-MCP 不穩定時的備援
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "twse.mcp.mock.enabled", havingValue = "true")
public class MockTwseMcpClient {
    
    private final Random random = new Random();
    
    // 模擬股票資料
    private final Map<String, String> mockStocks = Map.of(
        "2330", "台積電",
        "2317", "鴻海",
        "2454", "聯發科",
        "2881", "富邦金",
        "2882", "國泰金",
        "2412", "中華電",
        "2303", "聯電",
        "1301", "台塑",
        "1303", "南亞",
        "2002", "中鋼"
    );
    
    public CompletableFuture<TwseStockData> getStockData(String stockCode) {
        log.debug("Mock: Fetching stock data for code: {}", stockCode);
        
        if (!mockStocks.containsKey(stockCode)) {
            return CompletableFuture.failedFuture(new StockNotFoundException(stockCode));
        }
        
        // 模擬網路延遲
        return CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(100 + random.nextInt(200)); // 100-300ms 延遲
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            return generateMockStockData(stockCode);
        });
    }
    
    public CompletableFuture<List<TwseStockData>> getBatchStockData(List<String> stockCodes) {
        log.debug("Mock: Fetching batch stock data for {} codes", stockCodes.size());
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(200 + random.nextInt(300)); // 200-500ms 延遲
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            return stockCodes.stream()
                    .filter(mockStocks::containsKey)
                    .map(this::generateMockStockData)
                    .toList();
        });
    }
    
    public CompletableFuture<Boolean> validateStockCode(String stockCode) {
        log.debug("Mock: Validating stock code: {}", stockCode);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(50 + random.nextInt(100)); // 50-150ms 延遲
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            return mockStocks.containsKey(stockCode);
        });
    }
    
    private TwseStockData generateMockStockData(String stockCode) {
        String stockName = mockStocks.get(stockCode);
        
        // 生成模擬價格資料
        BigDecimal basePrice = getBasePrice(stockCode);
        BigDecimal change = generatePriceChange(basePrice);
        BigDecimal currentPrice = basePrice.add(change);
        BigDecimal changePercent = change.divide(basePrice, 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        
        // 生成 OHLC 資料
        BigDecimal high = currentPrice.add(BigDecimal.valueOf(random.nextDouble() * 10));
        BigDecimal low = currentPrice.subtract(BigDecimal.valueOf(random.nextDouble() * 10));
        BigDecimal open = low.add(high.subtract(low).multiply(BigDecimal.valueOf(random.nextDouble())));
        
        return TwseStockData.builder()
                .code(stockCode)
                .name(stockName)
                .closingPrice(currentPrice)
                .change(change)
                .changePercent(changePercent)
                .openingPrice(open)
                .highestPrice(high)
                .lowestPrice(low)
                .tradeVolume((long) (1000000 + random.nextInt(50000000))) // 100萬-5000萬
                .tradeValue(currentPrice.multiply(BigDecimal.valueOf(1000000 + random.nextInt(10000000))))
                .transaction((long) (1000 + random.nextInt(10000)))
                .tradeDate(LocalDateTime.now().toLocalDate().toString())
                .tradeTime(LocalDateTime.now().toLocalTime().toString())
                .build();
    }
    
    private BigDecimal getBasePrice(String stockCode) {
        // 根據股票代碼設定基準價格
        return switch (stockCode) {
            case "2330" -> BigDecimal.valueOf(580.0); // 台積電
            case "2317" -> BigDecimal.valueOf(110.0); // 鴻海
            case "2454" -> BigDecimal.valueOf(800.0); // 聯發科
            case "2881" -> BigDecimal.valueOf(75.0);  // 富邦金
            case "2882" -> BigDecimal.valueOf(65.0);  // 國泰金
            case "2412" -> BigDecimal.valueOf(125.0); // 中華電
            case "2303" -> BigDecimal.valueOf(45.0);  // 聯電
            case "1301" -> BigDecimal.valueOf(95.0);  // 台塑
            case "1303" -> BigDecimal.valueOf(80.0);  // 南亞
            case "2002" -> BigDecimal.valueOf(35.0);  // 中鋼
            default -> BigDecimal.valueOf(100.0);
        };
    }
    
    private BigDecimal generatePriceChange(BigDecimal basePrice) {
        // 生成 -5% 到 +5% 的價格變動
        double changePercent = (random.nextDouble() - 0.5) * 0.1; // -5% to +5%
        return basePrice.multiply(BigDecimal.valueOf(changePercent));
    }
}