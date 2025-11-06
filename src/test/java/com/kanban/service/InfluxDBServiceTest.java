package com.kanban.service;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.QueryApi;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import com.kanban.domain.entity.StockSnapshot;
import com.kanban.dto.OhlcDataDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InfluxDB 服務測試")
public class InfluxDBServiceTest {

    @Mock
    private InfluxDBClient influxDBClient;

    @Mock
    private WriteApiBlocking writeApiBlocking;

    @Mock
    private QueryApi queryApi;

    @InjectMocks
    private InfluxDBService influxDBService;

    private StockSnapshot testStockSnapshot;
    private List<StockSnapshot> testBatchSnapshots;

    @BeforeEach
    void setUp() {
        // 設定配置值
        ReflectionTestUtils.setField(influxDBService, "bucket", "stock_data");
        ReflectionTestUtils.setField(influxDBService, "organization", "kanban");

        // 準備測試資料
        testStockSnapshot = createTestStockSnapshot();
        testBatchSnapshots = createTestBatchSnapshots();

        // Mock InfluxDB 客戶端
        when(influxDBClient.getWriteApiBlocking()).thenReturn(writeApiBlocking);
        when(influxDBClient.getQueryApi()).thenReturn(queryApi);
    }

    @Nested
    @DisplayName("資料寫入測試")
    class DataWriteTests {

        @Test
        @DisplayName("應該成功寫入單一股票快照")
        void writeStockSnapshot_ShouldWriteSuccessfully() {
            // Given
            doNothing().when(writeApiBlocking).writePoint(eq("stock_data"), eq("kanban"), any());

            // When
            CompletableFuture<Void> result = influxDBService.writeStockSnapshot(testStockSnapshot);

            // Then
            assertThat(result).isCompleted();
            verify(writeApiBlocking).writePoint(eq("stock_data"), eq("kanban"), any());
        }

        @Test
        @DisplayName("應該處理寫入失敗情況")
        void writeStockSnapshot_WriteFailure_ShouldThrowException() {
            // Given
            doThrow(new RuntimeException("InfluxDB write failed"))
                    .when(writeApiBlocking).writePoint(eq("stock_data"), eq("kanban"), any());

            // When & Then
            assertThatThrownBy(() -> influxDBService.writeStockSnapshot(testStockSnapshot))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("InfluxDB write failed");
        }

        @Test
        @DisplayName("應該成功批次寫入多個股票快照")
        void writeBatchStockSnapshots_ShouldWriteBatchSuccessfully() {
            // Given
            doNothing().when(writeApiBlocking).writePoints(eq("stock_data"), eq("kanban"), anyList());

            // When
            CompletableFuture<Void> result = influxDBService.writeBatchStockSnapshots(testBatchSnapshots);

            // Then
            assertThat(result).isCompleted();
            verify(writeApiBlocking).writePoints(eq("stock_data"), eq("kanban"), anyList());
        }

        @Test
        @DisplayName("應該處理批次寫入失敗情況")
        void writeBatchStockSnapshots_BatchWriteFailure_ShouldThrowException() {
            // Given
            doThrow(new RuntimeException("InfluxDB batch write failed"))
                    .when(writeApiBlocking).writePoints(eq("stock_data"), eq("kanban"), anyList());

            // When & Then
            assertThatThrownBy(() -> influxDBService.writeBatchStockSnapshots(testBatchSnapshots))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("InfluxDB batch write failed");
        }
    }

    @Nested
    @DisplayName("資料降採樣準確性測試")
    class DataDownsamplingTests {

        @Test
        @DisplayName("應該正確查詢 OHLC 資料")
        void queryOHLCData_ShouldReturnCorrectData() {
            // Given
            List<FluxTable> mockTables = createMockFluxTables();
            when(queryApi.query(anyString(), eq("kanban"))).thenReturn(mockTables);

            // When
            List<OhlcDataDto> result = influxDBService.queryOHLCData("2330", "1m");

            // Then
            assertThat(result).isNotEmpty();
            assertThat(result).hasSize(2);
            
            OhlcDataDto firstRecord = result.get(0);
            assertThat(firstRecord.getOpen()).isEqualTo(new BigDecimal("580.00"));
            assertThat(firstRecord.getHigh()).isEqualTo(new BigDecimal("585.00"));
            assertThat(firstRecord.getLow()).isEqualTo(new BigDecimal("575.00"));
            assertThat(firstRecord.getClose()).isEqualTo(new BigDecimal("582.00"));
            assertThat(firstRecord.getVolume()).isEqualTo(1000000L);
            assertThat(firstRecord.getDate()).isNotNull();

            verify(queryApi).query(anyString(), eq("kanban"));
        }

        @Test
        @DisplayName("應該驗證 OHLC 資料邏輯正確性")
        void queryOHLCData_ShouldHaveValidOHLCLogic() {
            // Given
            List<FluxTable> mockTables = createValidOHLCFluxTables();
            when(queryApi.query(anyString(), eq("kanban"))).thenReturn(mockTables);

            // When
            List<OhlcDataDto> result = influxDBService.queryOHLCData("2330", "1m");

            // Then
            assertThat(result).isNotEmpty();
            
            for (OhlcDataDto ohlc : result) {
                // 驗證 OHLC 邏輯：high >= max(open, close), low <= min(open, close)
                BigDecimal maxPrice = ohlc.getOpen().max(ohlc.getClose());
                BigDecimal minPrice = ohlc.getOpen().min(ohlc.getClose());
                
                assertThat(ohlc.getHigh()).isGreaterThanOrEqualTo(maxPrice);
                assertThat(ohlc.getLow()).isLessThanOrEqualTo(minPrice);
                assertThat(ohlc.getVolume()).isPositive();
            }
        }

        @Test
        @DisplayName("應該處理查詢失敗情況")
        void queryOHLCData_QueryFailure_ShouldReturnEmptyList() {
            // Given
            when(queryApi.query(anyString(), eq("kanban")))
                    .thenThrow(new RuntimeException("Query failed"));

            // When
            List<OhlcDataDto> result = influxDBService.queryOHLCData("2330", "1m");

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("應該正確查詢最新資料")
        void queryLatestData_ShouldReturnLatestRecord() {
            // Given
            List<FluxTable> mockTables = createMockFluxTables();
            when(queryApi.query(anyString(), eq("kanban"))).thenReturn(mockTables);

            // When
            OhlcDataDto result = influxDBService.queryLatestData("2330");

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getOpen()).isEqualTo(new BigDecimal("580.00"));
            assertThat(result.getClose()).isEqualTo(new BigDecimal("582.00"));
            assertThat(result.getVolume()).isEqualTo(1000000L);
        }

        @Test
        @DisplayName("應該處理無資料情況")
        void queryLatestData_NoData_ShouldReturnNull() {
            // Given
            when(queryApi.query(anyString(), eq("kanban"))).thenReturn(new ArrayList<>());

            // When
            OhlcDataDto result = influxDBService.queryLatestData("9999");

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("應該正確處理不同時間範圍查詢")
        void queryOHLCData_DifferentTimeRanges_ShouldGenerateCorrectQueries() {
            // Given
            String[] timeRanges = {"1m", "3m", "6m", "1y", "2y"};
            List<FluxTable> mockTables = createMockFluxTables();
            
            for (String timeRange : timeRanges) {
                when(queryApi.query(contains("range(start: -" + timeRange + ")"), eq("kanban")))
                        .thenReturn(mockTables);

                // When
                List<OhlcDataDto> result = influxDBService.queryOHLCData("2330", timeRange);

                // Then
                assertThat(result).isNotEmpty();
                verify(queryApi).query(contains("range(start: -" + timeRange + ")"), eq("kanban"));
            }
        }

        @Test
        @DisplayName("應該驗證資料降採樣的時間聚合正確性")
        void queryOHLCData_ShouldAggregateDataCorrectly() {
            // Given
            List<FluxTable> mockTables = createTimeAggregatedFluxTables();
            when(queryApi.query(anyString(), eq("kanban"))).thenReturn(mockTables);

            // When
            List<OhlcDataDto> result = influxDBService.queryOHLCData("2330", "1m");

            // Then
            assertThat(result).isNotEmpty();
            
            // 驗證資料按時間排序
            for (int i = 1; i < result.size(); i++) {
                LocalDate currentDate = result.get(i).getDate();
                LocalDate previousDate = result.get(i - 1).getDate();
                assertThat(currentDate).isAfterOrEqualTo(previousDate);
            }
        }
    }

    @Nested
    @DisplayName("資料保留策略測試")
    class DataRetentionTests {

        @Test
        @DisplayName("應該設定資料保留策略")
        void setupRetentionPolicy_ShouldExecuteWithoutError() {
            // When & Then - 應該不拋出異常
            assertThatCode(() -> influxDBService.setupRetentionPolicy())
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("應該設定資料降採樣任務")
        void setupDownsamplingTasks_ShouldExecuteWithoutError() {
            // When & Then - 應該不拋出異常
            assertThatCode(() -> influxDBService.setupDownsamplingTasks())
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("資料格式與轉換測試")
    class DataFormatTests {

        @Test
        @DisplayName("應該正確處理 BigDecimal 值轉換")
        void getBigDecimalValue_ShouldConvertCorrectly() {
            // Given
            FluxRecord mockRecord = mock(FluxRecord.class);
            when(mockRecord.getValueByKey("test_field")).thenReturn(580.50);

            // When - 使用反射調用私有方法進行測試
            // 這裡我們通過實際查詢來間接測試轉換邏輯
            List<FluxTable> mockTables = createMockFluxTables();
            when(queryApi.query(anyString(), eq("kanban"))).thenReturn(mockTables);
            
            List<OhlcDataDto> result = influxDBService.queryOHLCData("2330", "1m");

            // Then
            assertThat(result).isNotEmpty();
            assertThat(result.get(0).getOpen()).isInstanceOf(BigDecimal.class);
        }

        @Test
        @DisplayName("應該正確處理 Long 值轉換")
        void getLongValue_ShouldConvertCorrectly() {
            // Given & When - 通過實際查詢來測試
            List<FluxTable> mockTables = createMockFluxTables();
            when(queryApi.query(anyString(), eq("kanban"))).thenReturn(mockTables);
            
            List<OhlcDataDto> result = influxDBService.queryOHLCData("2330", "1m");

            // Then
            assertThat(result).isNotEmpty();
            assertThat(result.get(0).getVolume()).isInstanceOf(Long.class);
        }

        @Test
        @DisplayName("應該處理 null 值情況")
        void handleNullValues_ShouldReturnNullSafely() {
            // Given
            List<FluxTable> mockTables = createNullValueFluxTables();
            when(queryApi.query(anyString(), eq("kanban"))).thenReturn(mockTables);

            // When
            List<OhlcDataDto> result = influxDBService.queryOHLCData("2330", "1m");

            // Then - 應該能處理 null 值而不拋出異常
            assertThatCode(() -> influxDBService.queryOHLCData("2330", "1m"))
                    .doesNotThrowAnyException();
        }
    }

    // 測試資料建立方法
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
        snapshot.setMa5(new BigDecimal("580.50"));
        snapshot.setMa10(new BigDecimal("579.20"));
        snapshot.setMa20(new BigDecimal("577.80"));
        snapshot.setMa60(new BigDecimal("575.30"));
        snapshot.setRsi(new BigDecimal("65.5"));
        snapshot.setKdK(new BigDecimal("75.2"));
        snapshot.setKdD(new BigDecimal("72.8"));
        snapshot.setDataSource("TWSE-MCP");
        snapshot.setUpdatedAt(LocalDateTime.now());
        return snapshot;
    }

    private List<StockSnapshot> createTestBatchSnapshots() {
        List<StockSnapshot> snapshots = new ArrayList<>();
        String[] stockCodes = {"2330", "2454", "2317"};
        
        for (String code : stockCodes) {
            StockSnapshot snapshot = createTestStockSnapshot();
            snapshot.setCode(code);
            snapshots.add(snapshot);
        }
        
        return snapshots;
    }

    private List<FluxTable> createMockFluxTables() {
        List<FluxTable> tables = new ArrayList<>();
        FluxTable table = mock(FluxTable.class);
        
        List<FluxRecord> records = new ArrayList<>();
        
        // 第一筆記錄
        FluxRecord record1 = mock(FluxRecord.class);
        when(record1.getTime()).thenReturn(Instant.now().minusSeconds(3600));
        when(record1.getValueByKey("open_price")).thenReturn(580.00);
        when(record1.getValueByKey("high_price")).thenReturn(585.00);
        when(record1.getValueByKey("low_price")).thenReturn(575.00);
        when(record1.getValueByKey("current_price")).thenReturn(582.00);
        when(record1.getValueByKey("volume")).thenReturn(1000000L);
        records.add(record1);
        
        // 第二筆記錄
        FluxRecord record2 = mock(FluxRecord.class);
        when(record2.getTime()).thenReturn(Instant.now());
        when(record2.getValueByKey("open_price")).thenReturn(582.00);
        when(record2.getValueByKey("high_price")).thenReturn(587.00);
        when(record2.getValueByKey("low_price")).thenReturn(580.00);
        when(record2.getValueByKey("current_price")).thenReturn(585.00);
        when(record2.getValueByKey("volume")).thenReturn(1200000L);
        records.add(record2);
        
        when(table.getRecords()).thenReturn(records);
        tables.add(table);
        
        return tables;
    }

    private List<FluxTable> createValidOHLCFluxTables() {
        List<FluxTable> tables = new ArrayList<>();
        FluxTable table = mock(FluxTable.class);
        
        List<FluxRecord> records = new ArrayList<>();
        
        // 建立符合 OHLC 邏輯的測試資料
        FluxRecord record = mock(FluxRecord.class);
        when(record.getTime()).thenReturn(Instant.now());
        when(record.getValueByKey("open_price")).thenReturn(580.00);
        when(record.getValueByKey("high_price")).thenReturn(585.00); // >= max(open, close)
        when(record.getValueByKey("low_price")).thenReturn(575.00);  // <= min(open, close)
        when(record.getValueByKey("current_price")).thenReturn(582.00);
        when(record.getValueByKey("volume")).thenReturn(1000000L);
        records.add(record);
        
        when(table.getRecords()).thenReturn(records);
        tables.add(table);
        
        return tables;
    }

    private List<FluxTable> createTimeAggregatedFluxTables() {
        List<FluxTable> tables = new ArrayList<>();
        FluxTable table = mock(FluxTable.class);
        
        List<FluxRecord> records = new ArrayList<>();
        
        // 建立時間排序的測試資料
        for (int i = 0; i < 5; i++) {
            FluxRecord record = mock(FluxRecord.class);
            when(record.getTime()).thenReturn(Instant.now().minusSeconds(3600 * (5 - i)));
            when(record.getValueByKey("open_price")).thenReturn(580.00 + i);
            when(record.getValueByKey("high_price")).thenReturn(585.00 + i);
            when(record.getValueByKey("low_price")).thenReturn(575.00 + i);
            when(record.getValueByKey("current_price")).thenReturn(582.00 + i);
            when(record.getValueByKey("volume")).thenReturn(1000000L + (i * 100000L));
            records.add(record);
        }
        
        when(table.getRecords()).thenReturn(records);
        tables.add(table);
        
        return tables;
    }

    private List<FluxTable> createNullValueFluxTables() {
        List<FluxTable> tables = new ArrayList<>();
        FluxTable table = mock(FluxTable.class);
        
        List<FluxRecord> records = new ArrayList<>();
        
        FluxRecord record = mock(FluxRecord.class);
        when(record.getTime()).thenReturn(Instant.now());
        when(record.getValueByKey("open_price")).thenReturn(null);
        when(record.getValueByKey("high_price")).thenReturn(null);
        when(record.getValueByKey("low_price")).thenReturn(null);
        when(record.getValueByKey("current_price")).thenReturn(null);
        when(record.getValueByKey("volume")).thenReturn(null);
        records.add(record);
        
        when(table.getRecords()).thenReturn(records);
        tables.add(table);
        
        return tables;
    }
}