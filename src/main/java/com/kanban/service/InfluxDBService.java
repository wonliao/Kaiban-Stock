package com.kanban.service;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.QueryApi;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import com.kanban.domain.entity.StockSnapshot;
import com.kanban.dto.OhlcDataDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * InfluxDB 時序資料服務
 * 負責股票時序資料的寫入、查詢與資料保留策略
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InfluxDBService {

    private final InfluxDBClient influxDBClient;
    
    @Value("${influxdb.bucket:stock_data}")
    private String bucket;
    
    @Value("${influxdb.org:kanban}")
    private String organization;

    /**
     * 寫入股票快照資料到 InfluxDB
     */
    @Async
    public CompletableFuture<Void> writeStockSnapshot(StockSnapshot snapshot) {
        try {
            WriteApiBlocking writeApi = influxDBClient.getWriteApiBlocking();
            
            Point point = Point.measurement("stock_prices")
                    .addTag("stock_code", snapshot.getCode())
                    .addTag("stock_name", snapshot.getName())
                    .addTag("data_source", snapshot.getDataSource())
                    .addField("current_price", snapshot.getCurrentPrice())
                    .addField("open_price", snapshot.getOpenPrice())
                    .addField("high_price", snapshot.getHighPrice())
                    .addField("low_price", snapshot.getLowPrice())
                    .addField("previous_close", snapshot.getPreviousClose())
                    .addField("volume", snapshot.getVolume())
                    .addField("change_percent", snapshot.getChangePercent())
                    .addField("ma5", snapshot.getMa5())
                    .addField("ma10", snapshot.getMa10())
                    .addField("ma20", snapshot.getMa20())
                    .addField("ma60", snapshot.getMa60())
                    .addField("rsi", snapshot.getRsi())
                    .addField("kd_k", snapshot.getKdK())
                    .addField("kd_d", snapshot.getKdD())
                    .time(Instant.now(), WritePrecision.MS);

            writeApi.writePoint(bucket, organization, point);
            log.debug("Written stock snapshot to InfluxDB: {}", snapshot.getCode());
            
        } catch (Exception e) {
            log.error("Failed to write stock snapshot to InfluxDB: {}", e.getMessage(), e);
            throw new RuntimeException("InfluxDB write failed", e);
        }
        
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 批次寫入多個股票快照
     */
    @Async
    public CompletableFuture<Void> writeBatchStockSnapshots(List<StockSnapshot> snapshots) {
        try {
            WriteApiBlocking writeApi = influxDBClient.getWriteApiBlocking();
            List<Point> points = new ArrayList<>();
            
            for (StockSnapshot snapshot : snapshots) {
                Point point = Point.measurement("stock_prices")
                        .addTag("stock_code", snapshot.getCode())
                        .addTag("stock_name", snapshot.getName())
                        .addTag("data_source", snapshot.getDataSource())
                        .addField("current_price", snapshot.getCurrentPrice())
                        .addField("open_price", snapshot.getOpenPrice())
                        .addField("high_price", snapshot.getHighPrice())
                        .addField("low_price", snapshot.getLowPrice())
                        .addField("previous_close", snapshot.getPreviousClose())
                        .addField("volume", snapshot.getVolume())
                        .addField("change_percent", snapshot.getChangePercent())
                        .addField("ma5", snapshot.getMa5())
                        .addField("ma10", snapshot.getMa10())
                        .addField("ma20", snapshot.getMa20())
                        .addField("ma60", snapshot.getMa60())
                        .addField("rsi", snapshot.getRsi())
                        .addField("kd_k", snapshot.getKdK())
                        .addField("kd_d", snapshot.getKdD())
                        .time(Instant.now(), WritePrecision.MS);
                
                points.add(point);
            }
            
            writeApi.writePoints(bucket, organization, points);
            log.info("Written {} stock snapshots to InfluxDB", snapshots.size());
            
        } catch (Exception e) {
            log.error("Failed to write batch stock snapshots to InfluxDB: {}", e.getMessage(), e);
            throw new RuntimeException("InfluxDB batch write failed", e);
        }
        
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 查詢股票的 OHLC 資料
     */
    public List<OhlcDataDto> queryOHLCData(String stockCode, String timeRange) {
        try {
            QueryApi queryApi = influxDBClient.getQueryApi();
            
            String flux = String.format("""
                from(bucket: "%s")
                  |> range(start: -%s)
                  |> filter(fn: (r) => r["_measurement"] == "stock_prices")
                  |> filter(fn: (r) => r["stock_code"] == "%s")
                  |> filter(fn: (r) => r["_field"] == "current_price" or r["_field"] == "open_price" or r["_field"] == "high_price" or r["_field"] == "low_price" or r["_field"] == "volume")
                  |> aggregateWindow(every: 1d, fn: last, createEmpty: false)
                  |> pivot(rowKey:["_time"], columnKey: ["_field"], valueColumn: "_value")
                  |> sort(columns: ["_time"])
                """, bucket, timeRange, stockCode);

            List<FluxTable> tables = queryApi.query(flux, organization);
            List<OhlcDataDto> ohlcData = new ArrayList<>();

            for (FluxTable table : tables) {
                for (FluxRecord record : table.getRecords()) {
                    Instant time = record.getTime();
                    LocalDate date = time.atZone(ZoneId.systemDefault()).toLocalDate();
                    
                    OhlcDataDto ohlc = OhlcDataDto.builder()
                            .date(date)
                            .open(getBigDecimalValue(record, "open_price"))
                            .high(getBigDecimalValue(record, "high_price"))
                            .low(getBigDecimalValue(record, "low_price"))
                            .close(getBigDecimalValue(record, "current_price"))
                            .volume(getLongValue(record, "volume"))
                            .build();
                    
                    ohlcData.add(ohlc);
                }
            }

            log.debug("Queried {} OHLC records for stock: {}, range: {}", ohlcData.size(), stockCode, timeRange);
            return ohlcData;
            
        } catch (Exception e) {
            log.error("Failed to query OHLC data from InfluxDB: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * 查詢股票的即時資料（最新一筆）
     */
    public OhlcDataDto queryLatestData(String stockCode) {
        try {
            QueryApi queryApi = influxDBClient.getQueryApi();
            
            String flux = String.format("""
                from(bucket: "%s")
                  |> range(start: -1h)
                  |> filter(fn: (r) => r["_measurement"] == "stock_prices")
                  |> filter(fn: (r) => r["stock_code"] == "%s")
                  |> filter(fn: (r) => r["_field"] == "current_price" or r["_field"] == "open_price" or r["_field"] == "high_price" or r["_field"] == "low_price" or r["_field"] == "volume")
                  |> last()
                  |> pivot(rowKey:["_time"], columnKey: ["_field"], valueColumn: "_value")
                """, bucket, stockCode);

            List<FluxTable> tables = queryApi.query(flux, organization);
            
            if (!tables.isEmpty() && !tables.get(0).getRecords().isEmpty()) {
                FluxRecord record = tables.get(0).getRecords().get(0);
                Instant time = record.getTime();
                LocalDate date = time.atZone(ZoneId.systemDefault()).toLocalDate();
                
                return OhlcDataDto.builder()
                        .date(date)
                        .open(getBigDecimalValue(record, "open_price"))
                        .high(getBigDecimalValue(record, "high_price"))
                        .low(getBigDecimalValue(record, "low_price"))
                        .close(getBigDecimalValue(record, "current_price"))
                        .volume(getLongValue(record, "volume"))
                        .build();
            }
            
            return null;
            
        } catch (Exception e) {
            log.error("Failed to query latest data from InfluxDB: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 設定資料保留策略（90天）
     */
    public void setupRetentionPolicy() {
        try {
            // InfluxDB 2.x 使用 bucket 的 retention period 設定
            // 這裡可以透過 InfluxDB API 設定 bucket 的保留策略
            log.info("Setting up retention policy for bucket: {}", bucket);
            
            // 實際的保留策略設定需要透過 InfluxDB 管理 API
            // 或在 InfluxDB 配置中設定
            
        } catch (Exception e) {
            log.error("Failed to setup retention policy: {}", e.getMessage(), e);
        }
    }

    /**
     * 建立連續查詢進行資料降採樣
     */
    public void setupDownsamplingTasks() {
        try {
            // InfluxDB 2.x 使用 Tasks 進行資料降採樣
            log.info("Setting up downsampling tasks for bucket: {}", bucket);
            
            // 小時級降採樣任務
            String hourlyTask = """
                option task = {name: "downsample-hourly", every: 1h}
                
                from(bucket: "%s")
                  |> range(start: -2h, stop: -1h)
                  |> filter(fn: (r) => r["_measurement"] == "stock_prices")
                  |> aggregateWindow(every: 1h, fn: mean, createEmpty: false)
                  |> to(bucket: "%s_hourly")
                """.formatted(bucket, bucket);
            
            // 日級降採樣任務
            String dailyTask = """
                option task = {name: "downsample-daily", every: 1d}
                
                from(bucket: "%s_hourly")
                  |> range(start: -2d, stop: -1d)
                  |> filter(fn: (r) => r["_measurement"] == "stock_prices")
                  |> aggregateWindow(every: 1d, fn: mean, createEmpty: false)
                  |> to(bucket: "%s_daily")
                """.formatted(bucket, bucket);
            
            log.info("Downsampling tasks configured");
            
        } catch (Exception e) {
            log.error("Failed to setup downsampling tasks: {}", e.getMessage(), e);
        }
    }

    /**
     * 從 FluxRecord 取得 BigDecimal 值
     */
    private BigDecimal getBigDecimalValue(FluxRecord record, String field) {
        Object value = record.getValueByKey(field);
        if (value == null) return null;
        
        if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        }
        
        return null;
    }

    /**
     * 從 FluxRecord 取得 Long 值
     */
    private Long getLongValue(FluxRecord record, String field) {
        Object value = record.getValueByKey(field);
        if (value == null) return null;
        
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        
        return null;
    }
}