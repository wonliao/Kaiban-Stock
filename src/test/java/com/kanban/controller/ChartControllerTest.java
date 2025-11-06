package com.kanban.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kanban.dto.ChartDataDto;
import com.kanban.dto.OhlcDataDto;
import com.kanban.service.HistoricalDataService;
import com.kanban.service.InfluxDBService;
import com.kanban.service.SseConnectionManager;
import com.kanban.service.StockDataService;
import com.kanban.domain.entity.HistoricalPrice;
import com.kanban.domain.entity.StockSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChartController.class)
@DisplayName("圖表 API 控制器測試")
public class ChartControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private HistoricalDataService historicalDataService;

    @MockBean
    private StockDataService stockDataService;

    @MockBean
    private InfluxDBService influxDBService;

    @MockBean
    private SseConnectionManager sseConnectionManager;

    private List<HistoricalPrice> testHistoricalPrices;
    private List<OhlcDataDto> testOhlcData;
    private StockSnapshot testStockSnapshot;

    @BeforeEach
    void setUp() {
        // 準備測試資料
        testHistoricalPrices = createTestHistoricalPrices();
        testOhlcData = createTestOhlcData();
        testStockSnapshot = createTestStockSnapshot();
    }

    @Nested
    @DisplayName("不同時間範圍資料查詢測試")
    class TimeRangeQueryTests {

        @Test
        @WithMockUser(username = "testuser")
        @DisplayName("應該支援預設 30 天圖表資料查詢")
        void getChartData_DefaultPeriod_ShouldReturn30Days() throws Exception {
            // Given
            when(historicalDataService.getHistoricalPrices("2330", 30))
                    .thenReturn(testHistoricalPrices);

            // When & Then
            mockMvc.perform(get("/api/chart/stocks/2330"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.stockCode").value("2330"))
                    .andExpect(jsonPath("$.period").value("30d"))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(testHistoricalPrices.size()));

            verify(historicalDataService).getHistoricalPrices("2330", 30);
        }

        @Test
        @WithMockUser(username = "testuser")
        @DisplayName("應該支援自訂天數查詢")
        void getChartData_CustomDays_ShouldReturnCorrectPeriod() throws Exception {
            // Given
            when(historicalDataService.getHistoricalPrices("2330", 90))
                    .thenReturn(testHistoricalPrices);

            // When & Then
            mockMvc.perform(get("/api/chart/stocks/2330")
                            .param("days", "90"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.stockCode").value("2330"))
                    .andExpect(jsonPath("$.period").value("90d"))
                    .andExpect(jsonPath("$.data").isArray());

            verify(historicalDataService).getHistoricalPrices("2330", 90);
        }

        @Test
        @WithMockUser(username = "testuser")
        @DisplayName("應該限制最大查詢天數為 365 天")
        void getChartData_ExceedMaxDays_ShouldLimitTo365() throws Exception {
            // Given
            when(historicalDataService.getHistoricalPrices("2330", 365))
                    .thenReturn(testHistoricalPrices);

            // When & Then
            mockMvc.perform(get("/api/chart/stocks/2330")
                            .param("days", "500")) // 超過最大限制
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.period").value("365d"));

            verify(historicalDataService).getHistoricalPrices("2330", 365);
        }

        @Test
        @WithMockUser(username = "testuser")
        @DisplayName("應該支援日期範圍查詢")
        void getChartDataByDateRange_ShouldReturnCustomRange() throws Exception {
            // Given
            LocalDate startDate = LocalDate.of(2024, 1, 1);
            LocalDate endDate = LocalDate.of(2024, 1, 31);
            
            when(historicalDataService.getHistoricalPrices("2330", startDate, endDate))
                    .thenReturn(testHistoricalPrices);

            // When & Then
            mockMvc.perform(get("/api/chart/stocks/2330/range")
                            .param("startDate", "2024-01-01")
                            .param("endDate", "2024-01-31"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.stockCode").value("2330"))
                    .andExpect(jsonPath("$.period").value("custom"))
                    .andExpect(jsonPath("$.startDate").value("2024-01-01"))
                    .andExpect(jsonPath("$.endDate").value("2024-01-31"));

            verify(historicalDataService).getHistoricalPrices("2330", startDate, endDate);
        }

        @Test
        @WithMockUser(username = "testuser")
        @DisplayName("應該修正無效的日期範圍")
        void getChartDataByDateRange_InvalidRange_ShouldCorrectDates() throws Exception {
            // Given
            LocalDate startDate = LocalDate.of(2024, 1, 31);
            LocalDate endDate = LocalDate.of(2024, 2, 29); // 修正後的結束日期
            
            when(historicalDataService.getHistoricalPrices(eq("2330"), eq(startDate), any(LocalDate.class)))
                    .thenReturn(testHistoricalPrices);

            // When & Then - 結束日期早於開始日期
            mockMvc.perform(get("/api/chart/stocks/2330/range")
                            .param("startDate", "2024-01-31")
                            .param("endDate", "2024-01-01")) // 無效範圍
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.stockCode").value("2330"));

            verify(historicalDataService).getHistoricalPrices(eq("2330"), eq(startDate), any(LocalDate.class));
        }

        @Test
        @WithMockUser(username = "testuser")
        @DisplayName("應該支援 OHLC 資料查詢與不同時間範圍")
        void getOHLCData_DifferentPeriods_ShouldReturnCorrectData() throws Exception {
            // Given
            String[] periods = {"1M", "3M", "6M", "1Y", "2Y"};
            
            for (String period : periods) {
                when(influxDBService.queryOHLCData("2330", period.toLowerCase()))
                        .thenReturn(testOhlcData);

                // When & Then
                mockMvc.perform(get("/api/chart/stocks/2330/ohlc")
                                .param("period", period))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.stockCode").value("2330"))
                        .andExpect(jsonPath("$.period").value(period))
                        .andExpect(jsonPath("$.data").isArray());

                verify(influxDBService).queryOHLCData("2330", period.toLowerCase());
            }
        }

        @Test
        @WithMockUser(username = "testuser")
        @DisplayName("InfluxDB 查詢失敗時應該回退到 PostgreSQL")
        void getOHLCData_InfluxDBFails_ShouldFallbackToPostgreSQL() throws Exception {
            // Given
            when(influxDBService.queryOHLCData("2330", "3m"))
                    .thenThrow(new RuntimeException("InfluxDB connection failed"));
            when(historicalDataService.getHistoricalPrices("2330", 90))
                    .thenReturn(testHistoricalPrices);

            // When & Then
            mockMvc.perform(get("/api/chart/stocks/2330/ohlc")
                            .param("period", "3M"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.stockCode").value("2330"))
                    .andExpect(jsonPath("$.period").value("3M"));

            verify(influxDBService).queryOHLCData("2330", "3m");
            verify(historicalDataService).getHistoricalPrices("2330", 90);
        }
    }

    @Nested
    @DisplayName("SSE 連線穩定性與效能測試")
    class SseConnectionTests {

        @Test
        @WithMockUser(username = "testuser")
        @DisplayName("應該建立即時資料 SSE 連線")
        void getRealTimeData_ShouldCreateSseConnection() throws Exception {
            // Given
            SseEmitter mockEmitter = mock(SseEmitter.class);
            when(sseConnectionManager.createConnection("testuser", "stock:2330", 5000))
                    .thenReturn(mockEmitter);
            when(stockDataService.getSnapshot("2330"))
                    .thenReturn(testStockSnapshot);

            // When & Then
            mockMvc.perform(get("/api/chart/stocks/2330/realtime"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", "text/event-stream"));

            verify(sseConnectionManager).createConnection("testuser", "stock:2330", 5000);
            verify(stockDataService).getSnapshot("2330");
        }

        @Test
        @WithMockUser(username = "testuser")
        @DisplayName("應該支援自訂更新間隔")
        void getRealTimeData_CustomInterval_ShouldUseSpecifiedInterval() throws Exception {
            // Given
            SseEmitter mockEmitter = mock(SseEmitter.class);
            when(sseConnectionManager.createConnection("testuser", "stock:2330", 10000))
                    .thenReturn(mockEmitter);
            when(stockDataService.getSnapshot("2330"))
                    .thenReturn(testStockSnapshot);

            // When & Then
            mockMvc.perform(get("/api/chart/stocks/2330/realtime")
                            .param("intervalMs", "10000"))
                    .andExpect(status().isOk());

            verify(sseConnectionManager).createConnection("testuser", "stock:2330", 10000);
        }

        @Test
        @WithMockUser(username = "testuser")
        @DisplayName("應該支援批次股票即時資料訂閱")
        void getBatchRealTimeData_ShouldCreateBatchConnection() throws Exception {
            // Given
            SseEmitter mockEmitter = mock(SseEmitter.class);
            when(sseConnectionManager.createConnection("testuser", "batch-stocks", 5000))
                    .thenReturn(mockEmitter);
            when(stockDataService.getSnapshot(anyString()))
                    .thenReturn(testStockSnapshot);

            // When & Then
            mockMvc.perform(get("/api/chart/stocks/batch/realtime")
                            .param("stockCodes", "2330,2454,2317"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", "text/event-stream"));

            verify(sseConnectionManager).createConnection("testuser", "batch-stocks", 5000);
            verify(stockDataService, times(3)).getSnapshot(anyString());
        }

        @Test
        @WithMockUser(username = "testuser")
        @DisplayName("應該取得 SSE 連線統計資訊")
        void getSseStats_ShouldReturnConnectionStats() throws Exception {
            // Given
            SseConnectionManager.ConnectionStats stats = SseConnectionManager.ConnectionStats.builder()
                    .activeConnections(50)
                    .maxConnections(1000)
                    .topicCount(10)
                    .utilizationRate(0.05)
                    .autoScaleEnabled(true)
                    .build();
            
            when(sseConnectionManager.getConnectionStats()).thenReturn(stats);

            // When & Then
            mockMvc.perform(get("/api/chart/sse/stats"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.activeConnections").value(50))
                    .andExpect(jsonPath("$.maxConnections").value(1000))
                    .andExpect(jsonPath("$.topicCount").value(10))
                    .andExpect(jsonPath("$.utilizationRate").value(0.05))
                    .andExpect(jsonPath("$.autoScaleEnabled").value(true));

            verify(sseConnectionManager).getConnectionStats();
        }

        @Test
        @WithMockUser(username = "testuser")
        @DisplayName("應該支援 POST 方式批次查詢即時資料")
        void postBatchRealTimeData_ShouldReturnBatchData() throws Exception {
            // Given
            List<String> stockCodes = List.of("2330", "2454", "2317");
            when(stockDataService.getSnapshot(anyString()))
                    .thenReturn(testStockSnapshot);

            // When & Then
            mockMvc.perform(post("/api/chart/stocks/batch/realtime")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(stockCodes)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.['2330']").exists())
                    .andExpect(jsonPath("$.['2454']").exists())
                    .andExpect(jsonPath("$.['2317']").exists());

            verify(stockDataService, times(3)).getSnapshot(anyString());
        }
    }

    @Nested
    @DisplayName("資料降採樣準確性測試")
    class DataDownsamplingTests {

        @Test
        @WithMockUser(username = "testuser")
        @DisplayName("應該正確處理日線資料（無需降採樣）")
        void getOHLCData_DailyInterval_ShouldReturnOriginalData() throws Exception {
            // Given
            when(influxDBService.queryOHLCData("2330", "1m"))
                    .thenReturn(testOhlcData);

            // When & Then
            mockMvc.perform(get("/api/chart/stocks/2330/ohlc")
                            .param("period", "1M")
                            .param("interval", "1d"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(testOhlcData.size()));

            verify(influxDBService).queryOHLCData("2330", "1m");
        }

        @Test
        @WithMockUser(username = "testuser")
        @DisplayName("應該驗證 OHLC 資料格式正確性")
        void getOHLCData_ShouldReturnValidOHLCFormat() throws Exception {
            // Given
            when(influxDBService.queryOHLCData("2330", "1m"))
                    .thenReturn(testOhlcData);

            // When & Then
            mockMvc.perform(get("/api/chart/stocks/2330/ohlc")
                            .param("period", "1M"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].date").exists())
                    .andExpect(jsonPath("$.data[0].open").exists())
                    .andExpect(jsonPath("$.data[0].high").exists())
                    .andExpect(jsonPath("$.data[0].low").exists())
                    .andExpect(jsonPath("$.data[0].close").exists())
                    .andExpect(jsonPath("$.data[0].volume").exists())
                    .andExpect(jsonPath("$.data[0].open").isNumber())
                    .andExpect(jsonPath("$.data[0].high").isNumber())
                    .andExpect(jsonPath("$.data[0].low").isNumber())
                    .andExpect(jsonPath("$.data[0].close").isNumber())
                    .andExpect(jsonPath("$.data[0].volume").isNumber());
        }

        @Test
        @WithMockUser(username = "testuser")
        @DisplayName("應該驗證 OHLC 資料邏輯正確性")
        void getOHLCData_ShouldHaveValidOHLCLogic() throws Exception {
            // Given
            List<OhlcDataDto> validOhlcData = List.of(
                    OhlcDataDto.builder()
                            .date(LocalDate.now())
                            .open(new BigDecimal("580.00"))
                            .high(new BigDecimal("585.00")) // high >= max(open, close)
                            .low(new BigDecimal("575.00"))  // low <= min(open, close)
                            .close(new BigDecimal("582.00"))
                            .volume(1000000L)
                            .build()
            );
            
            when(influxDBService.queryOHLCData("2330", "1m"))
                    .thenReturn(validOhlcData);

            // When & Then
            mockMvc.perform(get("/api/chart/stocks/2330/ohlc")
                            .param("period", "1M"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].high").value(585.00))
                    .andExpect(jsonPath("$.data[0].low").value(575.00))
                    .andExpect(jsonPath("$.data[0].open").value(580.00))
                    .andExpect(jsonPath("$.data[0].close").value(582.00));
        }

        @Test
        @WithMockUser(username = "testuser")
        @DisplayName("應該處理空資料情況")
        void getOHLCData_EmptyData_ShouldReturnEmptyArray() throws Exception {
            // Given
            when(influxDBService.queryOHLCData("9999", "1m"))
                    .thenReturn(new ArrayList<>());
            when(historicalDataService.getHistoricalPrices("9999", 30))
                    .thenReturn(new ArrayList<>());

            // When & Then
            mockMvc.perform(get("/api/chart/stocks/9999/ohlc")
                            .param("period", "1M"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(0));
        }

        @Test
        @WithMockUser(username = "testuser")
        @DisplayName("應該支援技術指標參數")
        void getOHLCData_WithIndicators_ShouldProcessIndicators() throws Exception {
            // Given
            when(influxDBService.queryOHLCData("2330", "3m"))
                    .thenReturn(testOhlcData);

            // When & Then
            mockMvc.perform(get("/api/chart/stocks/2330/ohlc")
                            .param("period", "3M")
                            .param("indicators", "ma5,ma20,rsi,kd"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.stockCode").value("2330"))
                    .andExpect(jsonPath("$.period").value("3M"));

            verify(influxDBService).queryOHLCData("2330", "3m");
        }
    }

    @Nested
    @DisplayName("錯誤處理與邊界條件測試")
    class ErrorHandlingTests {

        @Test
        @WithMockUser(username = "testuser")
        @DisplayName("應該處理無效股票代碼")
        void getChartData_InvalidStockCode_ShouldHandleGracefully() throws Exception {
            // Given
            when(historicalDataService.getHistoricalPrices("INVALID", 30))
                    .thenReturn(new ArrayList<>());

            // When & Then
            mockMvc.perform(get("/api/chart/stocks/INVALID"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.stockCode").value("INVALID"))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(0));
        }

        @Test
        @WithMockUser(username = "testuser")
        @DisplayName("應該處理負數天數參數")
        void getChartData_NegativeDays_ShouldUseDefault() throws Exception {
            // Given
            when(historicalDataService.getHistoricalPrices("2330", 30))
                    .thenReturn(testHistoricalPrices);

            // When & Then
            mockMvc.perform(get("/api/chart/stocks/2330")
                            .param("days", "-10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.period").value("30d"));

            verify(historicalDataService).getHistoricalPrices("2330", 30);
        }

        @Test
        @WithMockUser(username = "testuser")
        @DisplayName("應該處理無效日期格式")
        void getChartDataByDateRange_InvalidDateFormat_ShouldReturnBadRequest() throws Exception {
            mockMvc.perform(get("/api/chart/stocks/2330/range")
                            .param("startDate", "invalid-date")
                            .param("endDate", "2024-01-31"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("未認證使用者應該被拒絕存取")
        void getChartData_WithoutAuthentication_ShouldReturnUnauthorized() throws Exception {
            mockMvc.perform(get("/api/chart/stocks/2330"))
                    .andExpect(status().isUnauthorized());

            verify(historicalDataService, never()).getHistoricalPrices(anyString(), anyInt());
        }
    }

    // 測試資料建立方法
    private List<HistoricalPrice> createTestHistoricalPrices() {
        List<HistoricalPrice> prices = new ArrayList<>();
        LocalDate baseDate = LocalDate.now().minusDays(30);
        
        for (int i = 0; i < 30; i++) {
            HistoricalPrice price = new HistoricalPrice();
            price.setStockCode("2330");
            price.setTradeDate(baseDate.plusDays(i));
            price.setOpenPrice(new BigDecimal("580.00").add(new BigDecimal(i % 10)));
            price.setHighPrice(new BigDecimal("585.00").add(new BigDecimal(i % 10)));
            price.setLowPrice(new BigDecimal("575.00").add(new BigDecimal(i % 10)));
            price.setClosePrice(new BigDecimal("582.00").add(new BigDecimal(i % 10)));
            price.setVolume(1000000L + (i * 10000L));
            prices.add(price);
        }
        
        return prices;
    }

    private List<OhlcDataDto> createTestOhlcData() {
        List<OhlcDataDto> ohlcData = new ArrayList<>();
        LocalDate baseDate = LocalDate.now().minusDays(30);
        
        for (int i = 0; i < 30; i++) {
            OhlcDataDto ohlc = OhlcDataDto.builder()
                    .date(baseDate.plusDays(i))
                    .open(new BigDecimal("580.00").add(new BigDecimal(i % 10)))
                    .high(new BigDecimal("585.00").add(new BigDecimal(i % 10)))
                    .low(new BigDecimal("575.00").add(new BigDecimal(i % 10)))
                    .close(new BigDecimal("582.00").add(new BigDecimal(i % 10)))
                    .volume(1000000L + (i * 10000L))
                    .build();
            ohlcData.add(ohlc);
        }
        
        return ohlcData;
    }

    private StockSnapshot createTestStockSnapshot() {
        StockSnapshot snapshot = new StockSnapshot();
        snapshot.setCode("2330");
        snapshot.setName("台積電");
        snapshot.setCurrentPrice(new BigDecimal("582.00"));
        snapshot.setOpenPrice(new BigDecimal("580.00"));
        snapshot.setHighPrice(new BigDecimal("585.00"));
        snapshot.setLowPrice(new BigDecimal("575.00"));
        snapshot.setPreviousClose(new BigDecimal("578.00"));
        snapshot.setVolume(25000000L);
        snapshot.setChangePercent(new BigDecimal("0.69"));
        snapshot.setUpdatedAt(LocalDateTime.now());
        return snapshot;
    }
}