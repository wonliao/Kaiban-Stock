package com.kanban.client;

import com.kanban.dto.twse.TwseApiResponse;
import com.kanban.dto.twse.TwseStockData;
import com.kanban.exception.StockNotFoundException;
import com.kanban.exception.TwseMcpException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * TWSE-MCP API 客戶端
 * 實作斷路器模式、重試機制與超時控制
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TwseMcpClient {
    
    @Qualifier("twseMcpWebClient")
    private final WebClient webClient;
    
    /**
     * 取得單一股票即時資料
     */
    @CircuitBreaker(name = "twse-mcp", fallbackMethod = "fallbackGetStockData")
    @Retry(name = "twse-mcp")
    @TimeLimiter(name = "twse-mcp")
    @Bulkhead(name = "twse-mcp")
    public CompletableFuture<TwseStockData> getStockData(String stockCode) {
        log.debug("Fetching stock data for code: {}", stockCode);
        
        return webClient.get()
                .uri("/v1/exchangeReport/STOCK_DAY_ALL")
                .retrieve()
                .onStatus(HttpStatus.NOT_FOUND::equals, 
                    response -> Mono.error(new StockNotFoundException(stockCode)))
                .onStatus(HttpStatus::isError,
                    response -> Mono.error(new TwseMcpException(
                        "TWSE_API_ERROR", 
                        "TWSE API 回傳錯誤: " + response.statusCode(), 
                        response.statusCode().value())))
                .bodyToMono(new ParameterizedTypeReference<TwseApiResponse<List<String>>>() {})
                .map(response -> parseStockData(response, stockCode))
                .doOnSuccess(data -> log.debug("Successfully fetched data for {}: {}", stockCode, data))
                .doOnError(error -> log.error("Error fetching data for {}: {}", stockCode, error.getMessage()))
                .toFuture();
    }
    
    /**
     * 批次取得多檔股票資料
     */
    @CircuitBreaker(name = "twse-mcp", fallbackMethod = "fallbackGetBatchStockData")
    @Retry(name = "twse-mcp")
    @TimeLimiter(name = "twse-mcp")
    @Bulkhead(name = "twse-mcp")
    public CompletableFuture<List<TwseStockData>> getBatchStockData(List<String> stockCodes) {
        log.debug("Fetching batch stock data for {} codes", stockCodes.size());
        
        return webClient.get()
                .uri("/v1/exchangeReport/STOCK_DAY_ALL")
                .retrieve()
                .onStatus(HttpStatus::isError,
                    response -> Mono.error(new TwseMcpException(
                        "TWSE_API_ERROR", 
                        "TWSE API 回傳錯誤: " + response.statusCode(), 
                        response.statusCode().value())))
                .bodyToMono(new ParameterizedTypeReference<TwseApiResponse<List<String>>>() {})
                .map(response -> parseBatchStockData(response, stockCodes))
                .doOnSuccess(data -> log.debug("Successfully fetched batch data for {} stocks", data.size()))
                .doOnError(error -> log.error("Error fetching batch data: {}", error.getMessage()))
                .toFuture();
    }
    
    /**
     * 驗證股票代碼是否存在
     */
    @CircuitBreaker(name = "twse-mcp", fallbackMethod = "fallbackValidateStockCode")
    @Retry(name = "twse-mcp")
    public CompletableFuture<Boolean> validateStockCode(String stockCode) {
        log.debug("Validating stock code: {}", stockCode);
        
        return webClient.get()
                .uri("/v1/exchangeReport/STOCK_DAY_ALL")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<TwseApiResponse<List<String>>>() {})
                .map(response -> isStockCodeValid(response, stockCode))
                .onErrorReturn(false)
                .toFuture();
    }
    
    // Fallback methods
    public CompletableFuture<TwseStockData> fallbackGetStockData(String stockCode, Exception ex) {
        log.warn("Fallback triggered for stock {}: {}", stockCode, ex.getMessage());
        throw new TwseMcpException("TWSE_SERVICE_UNAVAILABLE", 
            "TWSE-MCP 服務暫時無法使用，請稍後再試", 503, ex);
    }
    
    public CompletableFuture<List<TwseStockData>> fallbackGetBatchStockData(List<String> stockCodes, Exception ex) {
        log.warn("Fallback triggered for batch request: {}", ex.getMessage());
        throw new TwseMcpException("TWSE_SERVICE_UNAVAILABLE", 
            "TWSE-MCP 服務暫時無法使用，請稍後再試", 503, ex);
    }
    
    public CompletableFuture<Boolean> fallbackValidateStockCode(String stockCode, Exception ex) {
        log.warn("Fallback triggered for validation {}: {}", stockCode, ex.getMessage());
        return CompletableFuture.completedFuture(false);
    }
    
    /**
     * 解析單一股票資料
     */
    private TwseStockData parseStockData(TwseApiResponse<List<String>> response, String stockCode) {
        if (!response.isSuccess() || response.getData() == null) {
            throw new TwseMcpException("INVALID_RESPONSE", "TWSE API 回應格式錯誤", 502);
        }
        
        // 尋找指定股票代碼的資料
        for (List<String> row : response.getData()) {
            if (row.size() >= 9 && stockCode.equals(row.get(0))) {
                return buildStockData(row);
            }
        }
        
        throw new StockNotFoundException(stockCode);
    }
    
    /**
     * 解析批次股票資料
     */
    private List<TwseStockData> parseBatchStockData(TwseApiResponse<List<String>> response, List<String> stockCodes) {
        if (!response.isSuccess() || response.getData() == null) {
            throw new TwseMcpException("INVALID_RESPONSE", "TWSE API 回應格式錯誤", 502);
        }
        
        return response.getData().stream()
                .filter(row -> row.size() >= 9 && stockCodes.contains(row.get(0)))
                .map(this::buildStockData)
                .toList();
    }
    
    /**
     * 驗證股票代碼
     */
    private boolean isStockCodeValid(TwseApiResponse<List<String>> response, String stockCode) {
        if (!response.isSuccess() || response.getData() == null) {
            return false;
        }
        
        return response.getData().stream()
                .anyMatch(row -> row.size() >= 1 && stockCode.equals(row.get(0)));
    }
    
    /**
     * 建構股票資料物件
     */
    private TwseStockData buildStockData(List<String> row) {
        try {
            return TwseStockData.builder()
                    .code(row.get(0))
                    .name(row.get(1))
                    .tradeVolume(parseLong(row.get(2)))
                    .transaction(parseLong(row.get(3)))
                    .tradeValue(parseBigDecimal(row.get(4)))
                    .openingPrice(parseBigDecimal(row.get(5)))
                    .highestPrice(parseBigDecimal(row.get(6)))
                    .lowestPrice(parseBigDecimal(row.get(7)))
                    .closingPrice(parseBigDecimal(row.get(8)))
                    .change(row.size() > 9 ? parseBigDecimal(row.get(9)) : null)
                    .changePercent(row.size() > 10 ? parseBigDecimal(row.get(10)) : null)
                    .tradeDate(LocalDateTime.now().toLocalDate().toString())
                    .tradeTime(LocalDateTime.now().toLocalTime().toString())
                    .build();
        } catch (Exception e) {
            log.error("Error parsing stock data for row: {}", row, e);
            throw new TwseMcpException("DATA_PARSE_ERROR", "股票資料解析失敗", 502, e);
        }
    }
    
    private java.math.BigDecimal parseBigDecimal(String value) {
        if (value == null || value.trim().isEmpty() || "--".equals(value)) {
            return null;
        }
        try {
            return new java.math.BigDecimal(value.replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    private Long parseLong(String value) {
        if (value == null || value.trim().isEmpty() || "--".equals(value)) {
            return null;
        }
        try {
            return Long.parseLong(value.replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}