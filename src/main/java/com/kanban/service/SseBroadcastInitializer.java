package com.kanban.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * SSE 廣播初始化器
 * 應用程式啟動時自動啟動多 Topic 分層廣播策略
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SseBroadcastInitializer implements ApplicationRunner {

    private final SseConnectionManager sseConnectionManager;
    private final StockDataService stockDataService;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("Initializing SSE multi-topic broadcast strategy");
        
        try {
            // 啟動多 Topic 分層廣播
            sseConnectionManager.startMultiTopicBroadcast(stockDataService);
            
            log.info("SSE multi-topic broadcast strategy started successfully");
            
        } catch (Exception e) {
            log.error("Failed to start SSE broadcast strategy: {}", e.getMessage(), e);
        }
    }
}