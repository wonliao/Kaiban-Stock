package com.kanban.controller;

import com.kanban.domain.entity.HistoricalPrice;
import com.kanban.domain.entity.StockSnapshot;
import com.kanban.dto.ChartDataDto;
import com.kanban.dto.OhlcDataDto;
import com.kanban.security.UserPrincipal;
import com.kanban.service.HistoricalDataService;
import com.kanban.service.InfluxDBService;
import com.kanban.service.SseConnectionManager;
import com.kanban.service.StockDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 圖表資料 API Controller
 * 提供股票歷史價格與圖表資料
 */
@RestController
@RequestMapping("/api/chart")
@RequiredArgsConstructor
@Slf4j
public class ChartController {

    private final HistoricalDataService historicalDataService;
    private final StockDataService stockDataService;
    private final InfluxDBService influxDBService;
    private final SseConnectionManager sseConnectionManager;

    /**
     * 取得股票圖表資料
     * @param stockCode 股票代碼
     * @param days 天數（預設 30 天）
     */
    @GetMapping("/stocks/{stockCode}")
    public ResponseEntity<ChartDataDto> getChartData(
            @PathVariable String stockCode,
            @RequestParam(defaultValue = "30") int days,
            Authentication authentication) {

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        String userId = userPrincipal.getId();
        log.debug("GET /api/chart/stocks/{} - user: {}, days: {}", stockCode, userId, days);

        // Validate days parameter
        if (days < 1) days = 30;
        if (days > 365) days = 365; // Maximum 1 year

        List<HistoricalPrice> historicalPrices = historicalDataService.getHistoricalPrices(stockCode, days);

        // Convert to OHLC format
        List<OhlcDataDto> ohlcData = historicalPrices.stream()
                .map(this::convertToOhlcDto)
                .collect(Collectors.toList());

        ChartDataDto chartData = ChartDataDto.builder()
                .stockCode(stockCode)
                .data(ohlcData)
                .period(days + "d")
                .build();

        return ResponseEntity.ok(chartData);
    }

    /**
     * 取得股票指定日期範圍的圖表資料
     * @param stockCode 股票代碼
     * @param startDate 開始日期
     * @param endDate 結束日期
     */
    @GetMapping("/stocks/{stockCode}/range")
    public ResponseEntity<ChartDataDto> getChartDataByDateRange(
            @PathVariable String stockCode,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Authentication authentication) {

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        String userId = userPrincipal.getId();
        log.debug("GET /api/chart/stocks/{}/range - user: {}, startDate: {}, endDate: {}",
                stockCode, userId, startDate, endDate);

        // Validate date range
        if (endDate.isBefore(startDate)) {
            endDate = startDate.plusDays(30);
        }

        List<HistoricalPrice> historicalPrices =
                historicalDataService.getHistoricalPrices(stockCode, startDate, endDate);

        // Convert to OHLC format
        List<OhlcDataDto> ohlcData = historicalPrices.stream()
                .map(this::convertToOhlcDto)
                .collect(Collectors.toList());

        ChartDataDto chartData = ChartDataDto.builder()
                .stockCode(stockCode)
                .data(ohlcData)
                .period("custom")
                .startDate(startDate)
                .endDate(endDate)
                .build();

        return ResponseEntity.ok(chartData);
    }

    /**
     * 取得 OHLC 資料 - 支援不同時間範圍與資料聚合
     * @param stockCode 股票代碼
     * @param period 時間範圍 (1M, 3M, 6M, 1Y, 2Y)
     * @param interval 資料間隔 (1d, 1w, 1M) - 可選
     * @param indicators 技術指標 (ma5,ma20,rsi,kd) - 可選
     */
    @GetMapping("/stocks/{stockCode}/ohlc")
    public ResponseEntity<ChartDataDto> getOHLCData(
            @PathVariable String stockCode,
            @RequestParam(defaultValue = "3M") String period,
            @RequestParam(required = false) String interval,
            @RequestParam(required = false) String indicators,
            Authentication authentication) {

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        String userId = userPrincipal.getId();
        log.debug("GET /api/chart/stocks/{}/ohlc - user: {}, period: {}, interval: {}, indicators: {}", 
                stockCode, userId, period, interval, indicators);

        // 解析時間範圍
        int days = parsePeriodToDays(period);
        
        // 優先從 InfluxDB 取得時序資料，回退到 PostgreSQL
        List<OhlcDataDto> ohlcData;
        try {
            ohlcData = influxDBService.queryOHLCData(stockCode, period.toLowerCase());
            if (ohlcData.isEmpty()) {
                // 回退到 PostgreSQL
                List<HistoricalPrice> historicalPrices = historicalDataService.getHistoricalPrices(stockCode, days);
                ohlcData = aggregateData(historicalPrices, interval);
            }
        } catch (Exception e) {
            log.warn("InfluxDB query failed, falling back to PostgreSQL: {}", e.getMessage());
            List<HistoricalPrice> historicalPrices = historicalDataService.getHistoricalPrices(stockCode, days);
            ohlcData = aggregateData(historicalPrices, interval);
        }
        
        // 計算技術指標（如果有要求）
        if (indicators != null && !indicators.isEmpty()) {
            ohlcData = addTechnicalIndicators(ohlcData, indicators);
        }

        ChartDataDto chartData = ChartDataDto.builder()
                .stockCode(stockCode)
                .data(ohlcData)
                .period(period)
                .build();

        return ResponseEntity.ok(chartData);
    }

    /**
     * 即時資料串流 - Server-Sent Events
     * @param stockCode 股票代碼
     * @param intervalMs 更新間隔（毫秒，最小 1000ms）
     */
    @GetMapping(value = "/stocks/{stockCode}/realtime", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter getRealTimeData(
            @PathVariable String stockCode,
            @RequestParam(defaultValue = "5000") int intervalMs,
            Authentication authentication) {

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        String userId = userPrincipal.getId();
        
        log.info("Creating SSE connection for stock: {}, user: {}, interval: {}ms", 
                stockCode, userId, intervalMs);

        // 使用 SseConnectionManager 建立連線
        SseEmitter emitter = sseConnectionManager.createConnection(userId, "stock:" + stockCode, intervalMs);

        // 立即發送初始資料
        try {
            var initialData = stockDataService.getSnapshot(stockCode);
            if (initialData != null) {
                emitter.send(SseEmitter.event()
                    .name("stock-update")
                    .data(convertSnapshotToOhlc(initialData))
                    .id(String.valueOf(System.currentTimeMillis())));
            }
        } catch (IOException e) {
            log.error("Failed to send initial data for stock {}: {}", stockCode, e.getMessage());
        }

        return emitter;
    }

    /**
     * 訂閱多檔股票即時資料
     */
    @GetMapping(value = "/stocks/batch/realtime", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter getBatchRealTimeData(
            @RequestParam List<String> stockCodes,
            @RequestParam(defaultValue = "5000") int intervalMs,
            Authentication authentication) {

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        String userId = userPrincipal.getId();
        
        log.info("Creating batch SSE connection for {} stocks, user: {}", stockCodes.size(), userId);

        // 建立批次連線
        SseEmitter emitter = sseConnectionManager.createConnection(userId, "batch-stocks", intervalMs);

        // 立即發送初始資料
        try {
            Map<String, OhlcDataDto> initialData = stockCodes.stream()
                    .filter(code -> code != null && !code.isEmpty())
                    .collect(Collectors.toMap(
                        code -> code,
                        code -> {
                            var snapshot = stockDataService.getSnapshot(code);
                            return snapshot != null ? convertSnapshotToOhlc(snapshot) : null;
                        }
                    ));
            
            // Remove null values
            initialData.entrySet().removeIf(entry -> entry.getValue() == null);

            emitter.send(SseEmitter.event()
                .name("batch-update")
                .data(initialData)
                .id(String.valueOf(System.currentTimeMillis())));
                
        } catch (IOException e) {
            log.error("Failed to send initial batch data: {}", e.getMessage());
        }

        return emitter;
    }

    /**
     * 取得 SSE 連線統計資訊
     */
    @GetMapping("/sse/stats")
    public ResponseEntity<SseConnectionManager.ConnectionStats> getSseStats(Authentication authentication) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        log.debug("Getting SSE stats for user: {}", userPrincipal.getId());
        
        return ResponseEntity.ok(sseConnectionManager.getConnectionStats());
    }

    /**
     * 取得多檔股票的即時報價
     */
    @PostMapping("/stocks/batch/realtime")
    public ResponseEntity<Map<String, OhlcDataDto>> getBatchRealTimeData(
            @RequestBody List<String> stockCodes,
            Authentication authentication) {

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        String userId = userPrincipal.getId();
        
        log.debug("GET batch realtime data for {} stocks, user: {}", stockCodes.size(), userId);

        Map<String, OhlcDataDto> result = new java.util.HashMap<>();
        
        for (String code : stockCodes) {
            var snapshot = stockDataService.getSnapshot(code);
            if (snapshot != null) {
                result.put(code, convertSnapshotToOhlc(snapshot));
            }
        }

        return ResponseEntity.ok(result);
    }

    /**
     * 解析時間範圍字串為天數
     */
    private int parsePeriodToDays(String period) {
        return switch (period.toUpperCase()) {
            case "1M" -> 30;
            case "3M" -> 90;
            case "6M" -> 180;
            case "1Y" -> 365;
            case "2Y" -> 730;
            default -> 90; // 預設 3 個月
        };
    }

    /**
     * 根據間隔聚合資料
     */
    private List<OhlcDataDto> aggregateData(List<HistoricalPrice> prices, String interval) {
        if (interval == null || "1d".equals(interval)) {
            // 日線資料，不需聚合
            return prices.stream()
                    .map(this::convertToOhlcDto)
                    .collect(Collectors.toList());
        }
        
        // TODO: 實作週線(1w)和月線(1M)聚合邏輯
        // 目前先返回日線資料
        return prices.stream()
                .map(this::convertToOhlcDto)
                .collect(Collectors.toList());
    }

    /**
     * 添加技術指標到 OHLC 資料
     */
    private List<OhlcDataDto> addTechnicalIndicators(List<OhlcDataDto> ohlcData, String indicators) {
        // TODO: 實作技術指標計算邏輯
        // 目前先返回原始資料
        return ohlcData;
    }

    /**
     * 將股票快照轉換為 OHLC 格式
     */
    private OhlcDataDto convertSnapshotToOhlc(com.kanban.domain.entity.StockSnapshot snapshot) {
        return OhlcDataDto.builder()
                .date(LocalDate.now())
                .open(snapshot.getOpenPrice())
                .high(snapshot.getHighPrice())
                .low(snapshot.getLowPrice())
                .close(snapshot.getCurrentPrice())
                .volume(snapshot.getVolume())
                .build();
    }

    /**
     * 將 HistoricalPrice 轉換為 OhlcDataDto
     */
    private OhlcDataDto convertToOhlcDto(HistoricalPrice price) {
        return OhlcDataDto.builder()
                .date(price.getTradeDate())
                .open(price.getOpenPrice())
                .high(price.getHighPrice())
                .low(price.getLowPrice())
                .close(price.getClosePrice())
                .volume(price.getVolume())
                .build();
    }
}
