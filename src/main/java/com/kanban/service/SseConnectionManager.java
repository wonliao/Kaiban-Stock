package com.kanban.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SSE 連線管理服務
 * 負責 Server-Sent Events 連線池管理、負載控制與多 Topic 分層廣播
 */
@Service
@Slf4j
public class SseConnectionManager {

    // 連線管理
    private final Map<String, Set<SseEmitter>> topicConnections = new ConcurrentHashMap<>();
    private final Map<String, SseEmitter> userConnections = new ConcurrentHashMap<>();
    private final AtomicInteger activeConnections = new AtomicInteger(0);
    
    // 執行緒池
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final ScheduledExecutorService cleanupScheduler = Executors.newScheduledThreadPool(1);
    
    // 配置參數
    @Value("${sse.max-connections:1000}")
    private int maxConnections;
    
    @Value("${sse.auto-scale.enabled:true}")
    private boolean autoScaleEnabled;
    
    @Value("${sse.auto-scale.threshold:0.8}")
    private double scaleThreshold;
    
    @Value("${sse.heartbeat.interval:30000}")
    private long heartbeatInterval;
    
    private static final int MIN_INTERVAL_MS = 1000;
    
    /**
     * 建立 SSE 連線
     */
    public SseEmitter createConnection(String userId, String topic, int intervalMs) {
        // 檢查連線數限制
        if (activeConnections.get() >= maxConnections) {
            throw new RuntimeException("超過最大連線數限制: " + maxConnections);
        }
        
        // 限制最小間隔
        int actualInterval = Math.max(intervalMs, MIN_INTERVAL_MS);
        
        log.info("Creating SSE connection - user: {}, topic: {}, interval: {}ms", 
                userId, topic, actualInterval);

        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        String connectionKey = userId + ":" + topic;
        
        // 儲存連線
        userConnections.put(connectionKey, emitter);
        topicConnections.computeIfAbsent(topic, k -> ConcurrentHashMap.newKeySet()).add(emitter);
        activeConnections.incrementAndGet();
        
        // 設定連線事件處理
        setupConnectionHandlers(emitter, connectionKey, topic);
        
        // 發送初始心跳
        sendHeartbeat(emitter, connectionKey);
        
        // 啟動定期心跳
        startHeartbeat(emitter, connectionKey);
        
        return emitter;
    }
    
    /**
     * 廣播訊息到指定 Topic
     */
    public void broadcastToTopic(String topic, Object data) {
        Set<SseEmitter> emitters = topicConnections.get(topic);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        
        log.debug("Broadcasting to topic: {}, connections: {}", topic, emitters.size());
        
        emitters.removeIf(emitter -> {
            try {
                emitter.send(SseEmitter.event()
                    .name("data-update")
                    .data(data)
                    .id(String.valueOf(System.currentTimeMillis())));
                return false; // 保留連線
            } catch (IOException e) {
                log.debug("Failed to send data to SSE connection: {}", e.getMessage());
                activeConnections.decrementAndGet();
                return true; // 移除失效連線
            }
        });
    }
    
    /**
     * 多 Topic 分層廣播策略
     */
    public void startMultiTopicBroadcast(StockDataService stockDataService) {
        log.info("Starting multi-topic broadcast strategy");
        
        // 熱門股票 - 高頻更新 (每秒)
        scheduler.scheduleAtFixedRate(() -> {
            broadcastHotStocks(stockDataService);
        }, 0, 1, TimeUnit.SECONDS);
        
        // 一般股票 - 標準頻率 (每5秒)
        scheduler.scheduleAtFixedRate(() -> {
            broadcastRegularStocks(stockDataService);
        }, 0, 5, TimeUnit.SECONDS);
        
        // 冷門股票 - 低頻更新 (每30秒)
        scheduler.scheduleAtFixedRate(() -> {
            broadcastColdStocks(stockDataService);
        }, 0, 30, TimeUnit.SECONDS);
        
        // 連線清理 (每分鐘)
        cleanupScheduler.scheduleAtFixedRate(this::cleanupConnections, 1, 1, TimeUnit.MINUTES);
    }
    
    /**
     * 發送訊息給特定使用者
     */
    public void sendToUser(String userId, String topic, Object data) {
        String connectionKey = userId + ":" + topic;
        SseEmitter emitter = userConnections.get(connectionKey);
        
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                    .name("user-message")
                    .data(data)
                    .id(String.valueOf(System.currentTimeMillis())));
            } catch (IOException e) {
                log.debug("Failed to send message to user {}: {}", userId, e.getMessage());
                removeConnection(connectionKey, topic, emitter);
            }
        }
    }
    
    /**
     * 取得連線統計資訊
     */
    public ConnectionStats getConnectionStats() {
        return ConnectionStats.builder()
                .activeConnections(activeConnections.get())
                .maxConnections(maxConnections)
                .topicCount(topicConnections.size())
                .utilizationRate((double) activeConnections.get() / maxConnections)
                .autoScaleEnabled(autoScaleEnabled)
                .build();
    }
    
    /**
     * 設定連線事件處理器
     */
    private void setupConnectionHandlers(SseEmitter emitter, String connectionKey, String topic) {
        emitter.onCompletion(() -> {
            removeConnection(connectionKey, topic, emitter);
            log.debug("SSE connection completed: {}", connectionKey);
        });
        
        emitter.onTimeout(() -> {
            removeConnection(connectionKey, topic, emitter);
            log.debug("SSE connection timeout: {}", connectionKey);
        });
        
        emitter.onError((ex) -> {
            removeConnection(connectionKey, topic, emitter);
            log.error("SSE connection error for {}: {}", connectionKey, ex.getMessage());
        });
    }
    
    /**
     * 移除連線
     */
    private void removeConnection(String connectionKey, String topic, SseEmitter emitter) {
        userConnections.remove(connectionKey);
        
        Set<SseEmitter> topicEmitters = topicConnections.get(topic);
        if (topicEmitters != null) {
            topicEmitters.remove(emitter);
            if (topicEmitters.isEmpty()) {
                topicConnections.remove(topic);
            }
        }
        
        activeConnections.decrementAndGet();
    }
    
    /**
     * 啟動心跳機制
     */
    private void startHeartbeat(SseEmitter emitter, String connectionKey) {
        scheduler.scheduleAtFixedRate(() -> {
            if (userConnections.containsKey(connectionKey)) {
                sendHeartbeat(emitter, connectionKey);
            }
        }, heartbeatInterval, heartbeatInterval, TimeUnit.MILLISECONDS);
    }
    
    /**
     * 發送心跳
     */
    private void sendHeartbeat(SseEmitter emitter, String connectionKey) {
        try {
            emitter.send(SseEmitter.event()
                .name("heartbeat")
                .data("ping")
                .id(String.valueOf(System.currentTimeMillis())));
        } catch (IOException e) {
            log.debug("Heartbeat failed for connection: {}", connectionKey);
            // 連線將在下次清理時被移除
        }
    }
    
    /**
     * 廣播熱門股票資料
     */
    private void broadcastHotStocks(StockDataService stockDataService) {
        try {
            // 熱門股票清單 (台積電、聯發科等)
            String[] hotStocks = {"2330", "2454", "2317", "2881", "2882"};
            
            for (String stockCode : hotStocks) {
                var snapshot = stockDataService.getSnapshot(stockCode);
                if (snapshot != null) {
                    broadcastToTopic("hot-stocks", Map.of(
                        "stockCode", stockCode,
                        "data", snapshot,
                        "updateType", "hot"
                    ));
                }
            }
        } catch (Exception e) {
            log.error("Error broadcasting hot stocks: {}", e.getMessage());
        }
    }
    
    /**
     * 廣播一般股票資料
     */
    private void broadcastRegularStocks(StockDataService stockDataService) {
        try {
            // 一般股票清單
            String[] regularStocks = {"2412", "2303", "1301", "1303", "2002"};
            
            for (String stockCode : regularStocks) {
                var snapshot = stockDataService.getSnapshot(stockCode);
                if (snapshot != null) {
                    broadcastToTopic("regular-stocks", Map.of(
                        "stockCode", stockCode,
                        "data", snapshot,
                        "updateType", "regular"
                    ));
                }
            }
        } catch (Exception e) {
            log.error("Error broadcasting regular stocks: {}", e.getMessage());
        }
    }
    
    /**
     * 廣播冷門股票資料
     */
    private void broadcastColdStocks(StockDataService stockDataService) {
        try {
            // 冷門股票清單
            String[] coldStocks = {"9910", "9921", "9930", "9940", "9950"};
            
            for (String stockCode : coldStocks) {
                var snapshot = stockDataService.getSnapshot(stockCode);
                if (snapshot != null) {
                    broadcastToTopic("cold-stocks", Map.of(
                        "stockCode", stockCode,
                        "data", snapshot,
                        "updateType", "cold"
                    ));
                }
            }
        } catch (Exception e) {
            log.error("Error broadcasting cold stocks: {}", e.getMessage());
        }
    }
    
    /**
     * 清理無效連線
     */
    private void cleanupConnections() {
        log.debug("Starting connection cleanup, active connections: {}", activeConnections.get());
        
        AtomicInteger cleanedCount = new AtomicInteger(0);
        
        // 清理 topic 連線
        for (Map.Entry<String, Set<SseEmitter>> entry : topicConnections.entrySet()) {
            Set<SseEmitter> emitters = entry.getValue();
            int beforeSize = emitters.size();
            
            emitters.removeIf(emitter -> {
                try {
                    // 嘗試發送測試訊息
                    emitter.send(SseEmitter.event().name("ping").data("test"));
                    return false; // 連線正常
                } catch (IOException e) {
                    activeConnections.decrementAndGet();
                    return true; // 移除無效連線
                }
            });
            
            cleanedCount.addAndGet(beforeSize - emitters.size());
            
            // 移除空的 topic
            if (emitters.isEmpty()) {
                topicConnections.remove(entry.getKey());
            }
        }
        
        // 清理使用者連線
        userConnections.entrySet().removeIf(entry -> {
            try {
                entry.getValue().send(SseEmitter.event().name("ping").data("test"));
                return false; // 連線正常
            } catch (IOException e) {
                activeConnections.decrementAndGet();
                cleanedCount.incrementAndGet();
                return true; // 移除無效連線
            }
        });
        
        if (cleanedCount.get() > 0) {
            log.info("Cleaned up {} invalid SSE connections, remaining: {}", 
                    cleanedCount.get(), activeConnections.get());
        }
        
        // 檢查是否需要自動擴展
        checkAutoScale();
    }
    
    /**
     * 檢查自動擴展條件
     */
    private void checkAutoScale() {
        if (!autoScaleEnabled) {
            return;
        }
        
        double utilizationRate = (double) activeConnections.get() / maxConnections;
        
        if (utilizationRate > scaleThreshold) {
            log.warn("SSE connection utilization rate: {:.2f}%, threshold: {:.2f}%", 
                    utilizationRate * 100, scaleThreshold * 100);
            
            // 這裡可以觸發自動擴展邏輯
            // 例如：增加 maxConnections 或啟動新的服務實例
        }
    }
    
    /**
     * 連線統計資訊
     */
    public static class ConnectionStats {
        private final int activeConnections;
        private final int maxConnections;
        private final int topicCount;
        private final double utilizationRate;
        private final boolean autoScaleEnabled;
        
        private ConnectionStats(Builder builder) {
            this.activeConnections = builder.activeConnections;
            this.maxConnections = builder.maxConnections;
            this.topicCount = builder.topicCount;
            this.utilizationRate = builder.utilizationRate;
            this.autoScaleEnabled = builder.autoScaleEnabled;
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        // Getters
        public int getActiveConnections() { return activeConnections; }
        public int getMaxConnections() { return maxConnections; }
        public int getTopicCount() { return topicCount; }
        public double getUtilizationRate() { return utilizationRate; }
        public boolean isAutoScaleEnabled() { return autoScaleEnabled; }
        
        public static class Builder {
            private int activeConnections;
            private int maxConnections;
            private int topicCount;
            private double utilizationRate;
            private boolean autoScaleEnabled;
            
            public Builder activeConnections(int activeConnections) {
                this.activeConnections = activeConnections;
                return this;
            }
            
            public Builder maxConnections(int maxConnections) {
                this.maxConnections = maxConnections;
                return this;
            }
            
            public Builder topicCount(int topicCount) {
                this.topicCount = topicCount;
                return this;
            }
            
            public Builder utilizationRate(double utilizationRate) {
                this.utilizationRate = utilizationRate;
                return this;
            }
            
            public Builder autoScaleEnabled(boolean autoScaleEnabled) {
                this.autoScaleEnabled = autoScaleEnabled;
                return this;
            }
            
            public ConnectionStats build() {
                return new ConnectionStats(this);
            }
        }
    }
}