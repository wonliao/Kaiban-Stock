package com.kanban.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SSE 連線管理服務測試")
public class SseConnectionManagerTest {

    @Mock
    private StockDataService stockDataService;

    @InjectMocks
    private SseConnectionManager sseConnectionManager;

    @BeforeEach
    void setUp() {
        // 設定配置值
        ReflectionTestUtils.setField(sseConnectionManager, "maxConnections", 1000);
        ReflectionTestUtils.setField(sseConnectionManager, "autoScaleEnabled", true);
        ReflectionTestUtils.setField(sseConnectionManager, "scaleThreshold", 0.8);
        ReflectionTestUtils.setField(sseConnectionManager, "heartbeatInterval", 30000L);
    }

    @Nested
    @DisplayName("SSE 連線建立與管理測試")
    class ConnectionManagementTests {

        @Test
        @DisplayName("應該成功建立 SSE 連線")
        void createConnection_ShouldCreateSuccessfully() {
            // When
            SseEmitter emitter = sseConnectionManager.createConnection("user1", "stock:2330", 5000);

            // Then
            assertThat(emitter).isNotNull();
            assertThat(emitter.getTimeout()).isEqualTo(Long.MAX_VALUE);
            
            SseConnectionManager.ConnectionStats stats = sseConnectionManager.getConnectionStats();
            assertThat(stats.getActiveConnections()).isEqualTo(1);
        }

        @Test
        @DisplayName("應該限制最小更新間隔為 1000ms")
        void createConnection_ShouldEnforceMinimumInterval() {
            // When
            SseEmitter emitter = sseConnectionManager.createConnection("user1", "stock:2330", 500);

            // Then
            assertThat(emitter).isNotNull();
            // 間隔應該被調整為最小值 1000ms（通過日誌或其他方式驗證）
        }

        @Test
        @DisplayName("應該拒絕超過最大連線數的請求")
        void createConnection_ExceedMaxConnections_ShouldThrowException() {
            // Given - 設定較小的最大連線數進行測試
            ReflectionTestUtils.setField(sseConnectionManager, "maxConnections", 2);
            
            // 建立最大數量的連線
            sseConnectionManager.createConnection("user1", "stock:2330", 5000);
            sseConnectionManager.createConnection("user2", "stock:2454", 5000);

            // When & Then
            assertThatThrownBy(() -> 
                sseConnectionManager.createConnection("user3", "stock:2317", 5000))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("超過最大連線數限制");
        }

        @Test
        @DisplayName("應該正確追蹤連線統計資訊")
        void getConnectionStats_ShouldReturnCorrectStats() {
            // Given
            sseConnectionManager.createConnection("user1", "stock:2330", 5000);
            sseConnectionManager.createConnection("user2", "stock:2454", 5000);

            // When
            SseConnectionManager.ConnectionStats stats = sseConnectionManager.getConnectionStats();

            // Then
            assertThat(stats.getActiveConnections()).isEqualTo(2);
            assertThat(stats.getMaxConnections()).isEqualTo(1000);
            assertThat(stats.getTopicCount()).isEqualTo(2);
            assertThat(stats.getUtilizationRate()).isEqualTo(0.002); // 2/1000
            assertThat(stats.isAutoScaleEnabled()).isTrue();
        }

        @Test
        @DisplayName("應該支援同一使用者多個 Topic 連線")
        void createConnection_SameUserMultipleTopics_ShouldAllowMultipleConnections() {
            // When
            SseEmitter emitter1 = sseConnectionManager.createConnection("user1", "stock:2330", 5000);
            SseEmitter emitter2 = sseConnectionManager.createConnection("user1", "stock:2454", 5000);

            // Then
            assertThat(emitter1).isNotNull();
            assertThat(emitter2).isNotNull();
            assertThat(emitter1).isNotSameAs(emitter2);
            
            SseConnectionManager.ConnectionStats stats = sseConnectionManager.getConnectionStats();
            assertThat(stats.getActiveConnections()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("廣播功能測試")
    class BroadcastTests {

        @Test
        @DisplayName("應該成功廣播訊息到指定 Topic")
        void broadcastToTopic_ShouldSendMessageToAllConnections() throws IOException {
            // Given
            SseEmitter mockEmitter1 = mock(SseEmitter.class);
            SseEmitter mockEmitter2 = mock(SseEmitter.class);
            
            // 手動設定連線到內部 Map（模擬已建立的連線）
            Map<String, Set<SseEmitter>> topicConnections = new ConcurrentHashMap<>();
            Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();
            emitters.add(mockEmitter1);
            emitters.add(mockEmitter2);
            topicConnections.put("test-topic", emitters);
            
            ReflectionTestUtils.setField(sseConnectionManager, "topicConnections", topicConnections);

            // When
            sseConnectionManager.broadcastToTopic("test-topic", "test-data");

            // Then
            verify(mockEmitter1).send(any(SseEmitter.SseEventBuilder.class));
            verify(mockEmitter2).send(any(SseEmitter.SseEventBuilder.class));
        }

        @Test
        @DisplayName("應該移除發送失敗的連線")
        void broadcastToTopic_FailedConnection_ShouldRemoveConnection() throws IOException {
            // Given
            SseEmitter mockEmitter1 = mock(SseEmitter.class);
            SseEmitter mockEmitter2 = mock(SseEmitter.class);
            
            // 設定一個連線發送失敗
            doThrow(new IOException("Connection failed"))
                    .when(mockEmitter1).send(any(SseEmitter.SseEventBuilder.class));
            
            Map<String, Set<SseEmitter>> topicConnections = new ConcurrentHashMap<>();
            Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();
            emitters.add(mockEmitter1);
            emitters.add(mockEmitter2);
            topicConnections.put("test-topic", emitters);
            
            ReflectionTestUtils.setField(sseConnectionManager, "topicConnections", topicConnections);
            
            AtomicInteger activeConnections = new AtomicInteger(2);
            ReflectionTestUtils.setField(sseConnectionManager, "activeConnections", activeConnections);

            // When
            sseConnectionManager.broadcastToTopic("test-topic", "test-data");

            // Then
            verify(mockEmitter1).send(any(SseEmitter.SseEventBuilder.class));
            verify(mockEmitter2).send(any(SseEmitter.SseEventBuilder.class));
            
            // 失敗的連線應該被移除
            assertThat(topicConnections.get("test-topic")).doesNotContain(mockEmitter1);
            assertThat(topicConnections.get("test-topic")).contains(mockEmitter2);
        }

        @Test
        @DisplayName("應該處理空 Topic 情況")
        void broadcastToTopic_EmptyTopic_ShouldHandleGracefully() {
            // When & Then - 應該不拋出異常
            assertThatCode(() -> 
                sseConnectionManager.broadcastToTopic("non-existent-topic", "test-data"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("應該成功發送訊息給特定使用者")
        void sendToUser_ShouldSendMessageToSpecificUser() throws IOException {
            // Given
            SseEmitter mockEmitter = mock(SseEmitter.class);
            
            Map<String, SseEmitter> userConnections = new ConcurrentHashMap<>();
            userConnections.put("user1:stock:2330", mockEmitter);
            
            ReflectionTestUtils.setField(sseConnectionManager, "userConnections", userConnections);

            // When
            sseConnectionManager.sendToUser("user1", "stock:2330", "test-data");

            // Then
            verify(mockEmitter).send(any(SseEmitter.SseEventBuilder.class));
        }

        @Test
        @DisplayName("應該處理使用者連線不存在的情況")
        void sendToUser_NonExistentUser_ShouldHandleGracefully() {
            // When & Then - 應該不拋出異常
            assertThatCode(() -> 
                sseConnectionManager.sendToUser("non-existent-user", "topic", "data"))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("多 Topic 分層廣播測試")
    class MultiTopicBroadcastTests {

        @Test
        @DisplayName("應該啟動多 Topic 廣播策略")
        void startMultiTopicBroadcast_ShouldInitializeBroadcastStrategy() {
            // When & Then - 應該不拋出異常
            assertThatCode(() -> 
                sseConnectionManager.startMultiTopicBroadcast(stockDataService))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("應該處理股票資料服務異常")
        void broadcastStocks_ServiceException_ShouldHandleGracefully() {
            // Given
            when(stockDataService.getSnapshot(anyString()))
                    .thenThrow(new RuntimeException("Service unavailable"));

            // When & Then - 廣播方法應該能處理異常而不中斷
            assertThatCode(() -> 
                sseConnectionManager.startMultiTopicBroadcast(stockDataService))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("連線清理與效能測試")
    class ConnectionCleanupTests {

        @Test
        @DisplayName("應該清理無效連線")
        void cleanupConnections_ShouldRemoveInvalidConnections() throws IOException {
            // Given
            SseEmitter validEmitter = mock(SseEmitter.class);
            SseEmitter invalidEmitter = mock(SseEmitter.class);
            
            // 設定無效連線會拋出異常
            doThrow(new IOException("Connection closed"))
                    .when(invalidEmitter).send(any(SseEmitter.SseEventBuilder.class));
            
            Map<String, Set<SseEmitter>> topicConnections = new ConcurrentHashMap<>();
            Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();
            emitters.add(validEmitter);
            emitters.add(invalidEmitter);
            topicConnections.put("test-topic", emitters);
            
            Map<String, SseEmitter> userConnections = new ConcurrentHashMap<>();
            userConnections.put("user1:topic1", validEmitter);
            userConnections.put("user2:topic2", invalidEmitter);
            
            ReflectionTestUtils.setField(sseConnectionManager, "topicConnections", topicConnections);
            ReflectionTestUtils.setField(sseConnectionManager, "userConnections", userConnections);
            
            AtomicInteger activeConnections = new AtomicInteger(2);
            ReflectionTestUtils.setField(sseConnectionManager, "activeConnections", activeConnections);

            // When - 通過反射調用私有清理方法
            Object result = ReflectionTestUtils.invokeMethod(sseConnectionManager, "cleanupConnections");

            // Then
            assertThat(topicConnections.get("test-topic")).contains(validEmitter);
            assertThat(topicConnections.get("test-topic")).doesNotContain(invalidEmitter);
            assertThat(userConnections).containsKey("user1:topic1");
            assertThat(userConnections).doesNotContainKey("user2:topic2");
        }

        @Test
        @DisplayName("應該檢查自動擴展條件")
        void checkAutoScale_HighUtilization_ShouldTriggerWarning() {
            // Given - 設定高使用率
            ReflectionTestUtils.setField(sseConnectionManager, "maxConnections", 100);
            
            AtomicInteger activeConnections = new AtomicInteger(85); // 85% 使用率
            ReflectionTestUtils.setField(sseConnectionManager, "activeConnections", activeConnections);

            // When & Then - 通過反射調用私有方法，應該觸發警告（通過日誌驗證，這裡主要確保不拋出異常）
            assertThatCode(() -> {
                Object result = ReflectionTestUtils.invokeMethod(sseConnectionManager, "checkAutoScale");
            }).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("應該移除空的 Topic")
        void cleanupConnections_EmptyTopic_ShouldRemoveTopic() {
            // Given
            Map<String, Set<SseEmitter>> topicConnections = new ConcurrentHashMap<>();
            topicConnections.put("empty-topic", ConcurrentHashMap.newKeySet());
            
            ReflectionTestUtils.setField(sseConnectionManager, "topicConnections", topicConnections);

            // When
            Object result = ReflectionTestUtils.invokeMethod(sseConnectionManager, "cleanupConnections");

            // Then
            assertThat(topicConnections).doesNotContainKey("empty-topic");
        }
    }

    @Nested
    @DisplayName("心跳機制測試")
    class HeartbeatTests {

        @Test
        @DisplayName("應該發送心跳訊息")
        void sendHeartbeat_ShouldSendPingMessage() throws IOException {
            // Given
            SseEmitter mockEmitter = mock(SseEmitter.class);

            // When - 通過反射調用私有方法
            Object result = ReflectionTestUtils.invokeMethod(sseConnectionManager, "sendHeartbeat", 
                    mockEmitter, "test-connection");

            // Then
            verify(mockEmitter).send(any(SseEmitter.SseEventBuilder.class));
        }

        @Test
        @DisplayName("應該處理心跳發送失敗")
        void sendHeartbeat_SendFailure_ShouldHandleGracefully() throws IOException {
            // Given
            SseEmitter mockEmitter = mock(SseEmitter.class);
            doThrow(new IOException("Heartbeat failed"))
                    .when(mockEmitter).send(any(SseEmitter.SseEventBuilder.class));

            // When & Then - 應該不拋出異常
            assertThatCode(() -> {
                Object result = ReflectionTestUtils.invokeMethod(sseConnectionManager, "sendHeartbeat", 
                        mockEmitter, "test-connection");
            }).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("效能與負載測試")
    class PerformanceTests {

        @Test
        @DisplayName("應該處理大量連線建立")
        void createConnection_ManyConnections_ShouldHandleLoad() {
            // Given
            int connectionCount = 100;

            // When
            for (int i = 0; i < connectionCount; i++) {
                sseConnectionManager.createConnection("user" + i, "stock:233" + i, 5000);
            }

            // Then
            SseConnectionManager.ConnectionStats stats = sseConnectionManager.getConnectionStats();
            assertThat(stats.getActiveConnections()).isEqualTo(connectionCount);
            assertThat(stats.getTopicCount()).isEqualTo(connectionCount);
        }

        @Test
        @DisplayName("應該處理大量廣播訊息")
        void broadcastToTopic_ManyMessages_ShouldHandleLoad() {
            // Given
            sseConnectionManager.createConnection("user1", "test-topic", 5000);
            
            // When & Then - 應該能處理大量廣播而不拋出異常
            assertThatCode(() -> {
                for (int i = 0; i < 1000; i++) {
                    sseConnectionManager.broadcastToTopic("test-topic", "message-" + i);
                }
            }).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("應該正確計算使用率")
        void getConnectionStats_UtilizationRate_ShouldCalculateCorrectly() {
            // Given
            ReflectionTestUtils.setField(sseConnectionManager, "maxConnections", 1000);
            
            // 建立 250 個連線
            for (int i = 0; i < 250; i++) {
                sseConnectionManager.createConnection("user" + i, "topic" + i, 5000);
            }

            // When
            SseConnectionManager.ConnectionStats stats = sseConnectionManager.getConnectionStats();

            // Then
            assertThat(stats.getUtilizationRate()).isEqualTo(0.25); // 250/1000 = 0.25
        }
    }
}