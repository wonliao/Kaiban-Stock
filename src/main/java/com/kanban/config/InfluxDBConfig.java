package com.kanban.config;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * InfluxDB 配置
 * 用於時序資料儲存與查詢
 */
@Configuration
@Slf4j
public class InfluxDBConfig {

    @Value("${influxdb.url:http://localhost:8086}")
    private String url;

    @Value("${influxdb.token:}")
    private String token;

    @Value("${influxdb.org:kanban}")
    private String organization;

    @Value("${influxdb.bucket:stock_data}")
    private String bucket;

    @Bean
    public InfluxDBClient influxDBClient() {
        log.info("Initializing InfluxDB client with URL: {}, org: {}, bucket: {}", url, organization, bucket);
        
        if (token.isEmpty()) {
            log.warn("InfluxDB token is empty, using default authentication");
            return InfluxDBClientFactory.create(url);
        }
        
        return InfluxDBClientFactory.create(url, token.toCharArray(), organization, bucket);
    }

    @Bean
    public String influxDBBucket() {
        return bucket;
    }

    @Bean
    public String influxDBOrg() {
        return organization;
    }
}