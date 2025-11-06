package com.kanban.controller;

import com.kanban.domain.entity.HistoricalPrice;
import com.kanban.dto.ChartDataDto;
import com.kanban.dto.OhlcDataDto;
import com.kanban.security.UserPrincipal;
import com.kanban.service.HistoricalDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
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
