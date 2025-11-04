package com.kanban.service;

import com.kanban.domain.entity.AuditLog;
import com.kanban.domain.entity.Card;
import com.kanban.domain.entity.StockSnapshot;
import com.kanban.domain.entity.User;
import com.kanban.domain.entity.Watchlist;
import com.kanban.dto.CardUpdateRequest;
import com.kanban.repository.AuditLogRepository;
import com.kanban.repository.CardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("看板稽核軌跡整合測試")
public class KanbanAuditIntegrationTest {
    
    @Mock
    private CardRepository cardRepository;
    
    @Mock
    private StockDataService stockDataService;
    
    @Mock
    private AuditLogService auditLogService;
    
    @Mock
    private AuditLogRepository auditLogRepository;
    
    @InjectMocks
    private KanbanService kanbanService;
    
    private User testUser;
    private Watchlist testWatchlist;
    private Card testCard;
    private StockSnapshot testSnapshot;
    
    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id("user1")
                .username("testuser")
                .email("test@example.com")
                .build();
        
        testWatchlist = Watchlist.builder()
                .id("watchlist1")
                .user(testUser)
                .name("我的觀察清單")
                .maxSize(500)
                .build();
        
        testCard = Card.builder()
                .id("card1")
                .user(testUser)
                .watchlist(testWatchlist)
                .stockCode("2330")
                .stockName("台積電")
                .status(Card.CardStatus.WATCH)
                .note("測試備註")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        testSnapshot = StockSnapshot.builder()
                .code("2330")
                .name("台積電")
                .currentPrice(new BigDecimal("580.00"))
                .changePercent(new BigDecimal("2.5"))
                .volume(25000000L)
                .ma20(new BigDecimal("575.00"))
                .rsi(new BigDecimal("65.5"))
                .build();
    }
    
    @Nested
    @DisplayName("稽核軌跡完整性測試")
    class AuditTrailIntegrityTests {
        
        @Test
        @DisplayName("狀態變更應該記錄完整的稽核軌跡")
        void updateCardStatus_ShouldCreateCompleteAuditTrail() {
            // Given
            CardUpdateRequest request = CardUpdateRequest.builder()
                    .status(Card.CardStatus.READY_TO_BUY)
                    .reason("技術指標突破")
                    .build();
            
            when(cardRepository.findByIdAndUserId("card1", "user1")).thenReturn(Optional.of(testCard));
            when(cardRepository.save(any(Card.class))).thenReturn(testCard);
            when(stockDataService.getSnapshot("2330")).thenReturn(testSnapshot);
            
            // When
            kanbanService.updateCard("user1", "card1", request);
            
            // Then - 驗證稽核日誌記錄
            ArgumentCaptor<String> userIdCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> cardIdCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Card.CardStatus> fromStatusCaptor = ArgumentCaptor.forClass(Card.CardStatus.class);
            ArgumentCaptor<Card.CardStatus> toStatusCaptor = ArgumentCaptor.forClass(Card.CardStatus.class);
            ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
            
            verify(auditLogService).logCardStatusChange(
                    userIdCaptor.capture(),
                    cardIdCaptor.capture(),
                    fromStatusCaptor.capture(),
                    toStatusCaptor.capture(),
                    reasonCaptor.capture()
            );
            
            assertThat(userIdCaptor.getValue()).isEqualTo("user1");
            assertThat(cardIdCaptor.getValue()).isEqualTo("card1");
            assertThat(fromStatusCaptor.getValue()).isEqualTo(Card.CardStatus.WATCH);
            assertThat(toStatusCaptor.getValue()).isEqualTo(Card.CardStatus.READY_TO_BUY);
            assertThat(reasonCaptor.getValue()).isEqualTo("技術指標突破");
        }
        
        @Test
        @DisplayName("只更新備註不應該記錄狀態變更稽核")
        void updateCardNoteOnly_ShouldNotCreateStatusAuditTrail() {
            // Given
            CardUpdateRequest request = CardUpdateRequest.builder()
                    .note("更新備註內容")
                    .build();
            
            when(cardRepository.findByIdAndUserId("card1", "user1")).thenReturn(Optional.of(testCard));
            when(cardRepository.save(any(Card.class))).thenReturn(testCard);
            when(stockDataService.getSnapshot("2330")).thenReturn(testSnapshot);
            
            // When
            kanbanService.updateCard("user1", "card1", request);
            
            // Then - 不應該記錄狀態變更稽核
            verify(auditLogService, never()).logCardStatusChange(
                    anyString(), anyString(), any(), any(), anyString()
            );
        }
        
        @Test
        @DisplayName("相同狀態更新不應該記錄稽核軌跡")
        void updateCardWithSameStatus_ShouldNotCreateAuditTrail() {
            // Given
            CardUpdateRequest request = CardUpdateRequest.builder()
                    .status(Card.CardStatus.WATCH) // 相同狀態
                    .reason("重複設定")
                    .build();
            
            when(cardRepository.findByIdAndUserId("card1", "user1")).thenReturn(Optional.of(testCard));
            when(cardRepository.save(any(Card.class))).thenReturn(testCard);
            when(stockDataService.getSnapshot("2330")).thenReturn(testSnapshot);
            
            // When
            kanbanService.updateCard("user1", "card1", request);
            
            // Then - 不應該記錄稽核軌跡
            verify(auditLogService, never()).logCardStatusChange(
                    anyString(), anyString(), any(), any(), anyString()
            );
        }
        
        @Test
        @DisplayName("多次狀態變更應該記錄完整的變更歷史")
        void multipleStatusChanges_ShouldCreateCompleteHistory() {
            // Given
            Card.CardStatus[] statusSequence = {
                Card.CardStatus.READY_TO_BUY,
                Card.CardStatus.HOLD,
                Card.CardStatus.SELL,
                Card.CardStatus.ALERTS
            };
            
            String[] reasons = {
                "技術指標突破",
                "執行買進",
                "獲利了結",
                "價格警示"
            };
            
            when(cardRepository.findByIdAndUserId("card1", "user1")).thenReturn(Optional.of(testCard));
            when(cardRepository.save(any(Card.class))).thenReturn(testCard);
            when(stockDataService.getSnapshot("2330")).thenReturn(testSnapshot);
            
            Card.CardStatus previousStatus = Card.CardStatus.WATCH;
            
            // When - 執行多次狀態變更
            for (int i = 0; i < statusSequence.length; i++) {
                CardUpdateRequest request = CardUpdateRequest.builder()
                        .status(statusSequence[i])
                        .reason(reasons[i])
                        .build();
                
                // 更新測試卡片的狀態以模擬實際變更
                testCard.setStatus(statusSequence[i]);
                
                kanbanService.updateCard("user1", "card1", request);
                
                // Then - 驗證每次變更都有記錄稽核軌跡
                verify(auditLogService).logCardStatusChange(
                        "user1",
                        "card1",
                        previousStatus,
                        statusSequence[i],
                        reasons[i]
                );
                
                previousStatus = statusSequence[i];
            }
            
            // 總共應該記錄 4 次稽核軌跡
            verify(auditLogService, times(4)).logCardStatusChange(
                    anyString(), anyString(), any(), any(), anyString()
            );
        }
        
        @Test
        @DisplayName("稽核軌跡應該包含時間戳記和追蹤ID")
        void auditTrail_ShouldIncludeTimestampAndTraceId() {
            // Given
            AuditLog mockAuditLog = AuditLog.builder()
                    .id("audit1")
                    .userId("user1")
                    .cardId("card1")
                    .action("CARD_STATUS_CHANGE")
                    .fromStatus("WATCH")
                    .toStatus("READY_TO_BUY")
                    .reason("技術指標突破")
                    .createdAt(LocalDateTime.now())
                    .traceId("trace-123")
                    .build();
            
            when(auditLogRepository.save(any(AuditLog.class))).thenReturn(mockAuditLog);
            
            // 直接測試 AuditLogService
            AuditLogService realAuditLogService = new AuditLogService(auditLogRepository);
            
            // When
            realAuditLogService.logCardStatusChange(
                    "user1", "card1", 
                    Card.CardStatus.WATCH, 
                    Card.CardStatus.READY_TO_BUY, 
                    "技術指標突破"
            );
            
            // Then
            ArgumentCaptor<AuditLog> auditLogCaptor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(auditLogCaptor.capture());
            
            AuditLog capturedAuditLog = auditLogCaptor.getValue();
            assertThat(capturedAuditLog.getUserId()).isEqualTo("user1");
            assertThat(capturedAuditLog.getCardId()).isEqualTo("card1");
            assertThat(capturedAuditLog.getAction()).isEqualTo("CARD_STATUS_CHANGE");
            assertThat(capturedAuditLog.getFromStatus()).isEqualTo("WATCH");
            assertThat(capturedAuditLog.getToStatus()).isEqualTo("READY_TO_BUY");
            assertThat(capturedAuditLog.getReason()).isEqualTo("技術指標突破");
            assertThat(capturedAuditLog.getTraceId()).isNotNull();
        }
    }
    
    @Nested
    @DisplayName("稽核軌跡查詢測試")
    class AuditTrailQueryTests {
        
        @Test
        @DisplayName("應該能查詢使用者的稽核軌跡")
        void getUserAuditLogs_ShouldReturnUserAuditHistory() {
            // Given
            AuditLog auditLog1 = AuditLog.builder()
                    .id("audit1")
                    .userId("user1")
                    .cardId("card1")
                    .action("CARD_STATUS_CHANGE")
                    .fromStatus("WATCH")
                    .toStatus("READY_TO_BUY")
                    .reason("技術指標突破")
                    .createdAt(LocalDateTime.now().minusHours(1))
                    .build();
            
            AuditLog auditLog2 = AuditLog.builder()
                    .id("audit2")
                    .userId("user1")
                    .cardId("card1")
                    .action("CARD_STATUS_CHANGE")
                    .fromStatus("READY_TO_BUY")
                    .toStatus("HOLD")
                    .reason("執行買進")
                    .createdAt(LocalDateTime.now())
                    .build();
            
            Pageable pageable = PageRequest.of(0, 10);
            Page<AuditLog> auditLogPage = new PageImpl<>(List.of(auditLog2, auditLog1)); // 按時間倒序
            
            when(auditLogRepository.findByUserIdOrderByCreatedAtDesc("user1", pageable))
                    .thenReturn(auditLogPage);
            
            AuditLogService realAuditLogService = new AuditLogService(auditLogRepository);
            
            // When
            Page<AuditLog> result = realAuditLogService.getUserAuditLogs("user1", pageable);
            
            // Then
            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getContent().get(0).getId()).isEqualTo("audit2"); // 最新的在前
            assertThat(result.getContent().get(1).getId()).isEqualTo("audit1");
            assertThat(result.getContent().get(0).getToStatus()).isEqualTo("HOLD");
            assertThat(result.getContent().get(1).getToStatus()).isEqualTo("READY_TO_BUY");
        }
        
        @Test
        @DisplayName("應該能查詢特定卡片的稽核軌跡")
        void getCardAuditLogs_ShouldReturnCardAuditHistory() {
            // Given
            AuditLog auditLog = AuditLog.builder()
                    .id("audit1")
                    .userId("user1")
                    .cardId("card1")
                    .action("CARD_STATUS_CHANGE")
                    .fromStatus("WATCH")
                    .toStatus("READY_TO_BUY")
                    .reason("技術指標突破")
                    .createdAt(LocalDateTime.now())
                    .build();
            
            Pageable pageable = PageRequest.of(0, 10);
            Page<AuditLog> auditLogPage = new PageImpl<>(List.of(auditLog));
            
            when(auditLogRepository.findByCardIdOrderByCreatedAtDesc("card1", pageable))
                    .thenReturn(auditLogPage);
            
            AuditLogService realAuditLogService = new AuditLogService(auditLogRepository);
            
            // When
            Page<AuditLog> result = realAuditLogService.getCardAuditLogs("card1", pageable);
            
            // Then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getCardId()).isEqualTo("card1");
            assertThat(result.getContent().get(0).getAction()).isEqualTo("CARD_STATUS_CHANGE");
        }
        
        @Test
        @DisplayName("稽核軌跡查詢應該支援分頁")
        void auditLogQuery_ShouldSupportPagination() {
            // Given
            List<AuditLog> allAuditLogs = List.of(
                    createAuditLog("audit1", "WATCH", "READY_TO_BUY"),
                    createAuditLog("audit2", "READY_TO_BUY", "HOLD"),
                    createAuditLog("audit3", "HOLD", "SELL")
            );
            
            // 第一頁 (0, 2)
            Pageable firstPage = PageRequest.of(0, 2);
            Page<AuditLog> firstPageResult = new PageImpl<>(
                    allAuditLogs.subList(0, 2), firstPage, allAuditLogs.size()
            );
            
            // 第二頁 (1, 2)
            Pageable secondPage = PageRequest.of(1, 2);
            Page<AuditLog> secondPageResult = new PageImpl<>(
                    allAuditLogs.subList(2, 3), secondPage, allAuditLogs.size()
            );
            
            when(auditLogRepository.findByUserIdOrderByCreatedAtDesc("user1", firstPage))
                    .thenReturn(firstPageResult);
            when(auditLogRepository.findByUserIdOrderByCreatedAtDesc("user1", secondPage))
                    .thenReturn(secondPageResult);
            
            AuditLogService realAuditLogService = new AuditLogService(auditLogRepository);
            
            // When & Then - 第一頁
            Page<AuditLog> page1 = realAuditLogService.getUserAuditLogs("user1", firstPage);
            assertThat(page1.getContent()).hasSize(2);
            assertThat(page1.getTotalElements()).isEqualTo(3);
            assertThat(page1.getTotalPages()).isEqualTo(2);
            assertThat(page1.hasNext()).isTrue();
            assertThat(page1.hasPrevious()).isFalse();
            
            // When & Then - 第二頁
            Page<AuditLog> page2 = realAuditLogService.getUserAuditLogs("user1", secondPage);
            assertThat(page2.getContent()).hasSize(1);
            assertThat(page2.getTotalElements()).isEqualTo(3);
            assertThat(page2.getTotalPages()).isEqualTo(2);
            assertThat(page2.hasNext()).isFalse();
            assertThat(page2.hasPrevious()).isTrue();
        }
        
        @Test
        @DisplayName("應該能封存舊的稽核軌跡")
        void archiveOldAuditLogs_ShouldDeleteOldEntries() {
            // Given
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(90);
            when(auditLogRepository.deleteByCreatedAtBefore(cutoffDate)).thenReturn(150L);
            
            AuditLogService realAuditLogService = new AuditLogService(auditLogRepository);
            
            // When
            realAuditLogService.archiveOldLogs(cutoffDate);
            
            // Then
            verify(auditLogRepository).deleteByCreatedAtBefore(cutoffDate);
        }
        
        private AuditLog createAuditLog(String id, String fromStatus, String toStatus) {
            return AuditLog.builder()
                    .id(id)
                    .userId("user1")
                    .cardId("card1")
                    .action("CARD_STATUS_CHANGE")
                    .fromStatus(fromStatus)
                    .toStatus(toStatus)
                    .reason("測試原因")
                    .createdAt(LocalDateTime.now())
                    .traceId("trace-" + id)
                    .build();
        }
    }
    
    @Nested
    @DisplayName("稽核軌跡資料完整性測試")
    class AuditTrailDataIntegrityTests {
        
        @Test
        @DisplayName("稽核軌跡應該記錄所有必要欄位")
        void auditTrail_ShouldRecordAllRequiredFields() {
            // Given
            AuditLogService realAuditLogService = new AuditLogService(auditLogRepository);
            
            // When
            realAuditLogService.logCardStatusChange(
                    "user1", "card1", 
                    Card.CardStatus.WATCH, 
                    Card.CardStatus.READY_TO_BUY, 
                    "技術指標突破"
            );
            
            // Then
            ArgumentCaptor<AuditLog> auditLogCaptor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(auditLogCaptor.capture());
            
            AuditLog auditLog = auditLogCaptor.getValue();
            
            // 驗證所有必要欄位都有值
            assertThat(auditLog.getUserId()).isNotNull().isEqualTo("user1");
            assertThat(auditLog.getCardId()).isNotNull().isEqualTo("card1");
            assertThat(auditLog.getAction()).isNotNull().isEqualTo("CARD_STATUS_CHANGE");
            assertThat(auditLog.getFromStatus()).isNotNull().isEqualTo("WATCH");
            assertThat(auditLog.getToStatus()).isNotNull().isEqualTo("READY_TO_BUY");
            assertThat(auditLog.getReason()).isNotNull().isEqualTo("技術指標突破");
            assertThat(auditLog.getTraceId()).isNotNull();
            
            // 驗證追蹤 ID 格式 (UUID)
            assertThat(auditLog.getTraceId()).matches(
                    "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
            );
        }
        
        @Test
        @DisplayName("觀察清單變更應該記錄稽核軌跡")
        void watchlistChange_ShouldCreateAuditTrail() {
            // Given
            AuditLogService realAuditLogService = new AuditLogService(auditLogRepository);
            
            // When
            realAuditLogService.logWatchlistChange("user1", "ADD_STOCK", "2330", "新增台積電至觀察清單");
            
            // Then
            ArgumentCaptor<AuditLog> auditLogCaptor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(auditLogCaptor.capture());
            
            AuditLog auditLog = auditLogCaptor.getValue();
            assertThat(auditLog.getUserId()).isEqualTo("user1");
            assertThat(auditLog.getAction()).isEqualTo("ADD_STOCK");
            assertThat(auditLog.getReason()).isEqualTo("新增台積電至觀察清單");
            assertThat(auditLog.getCardId()).isNull(); // 觀察清單操作不涉及特定卡片
        }
        
        @Test
        @DisplayName("稽核軌跡應該處理空值情況")
        void auditTrail_ShouldHandleNullValues() {
            // Given
            AuditLogService realAuditLogService = new AuditLogService(auditLogRepository);
            
            // When - 測試 fromStatus 為 null 的情況 (新建卡片)
            realAuditLogService.logCardStatusChange(
                    "user1", "card1", 
                    null, // 新建卡片沒有前一個狀態
                    Card.CardStatus.WATCH, 
                    "新建卡片"
            );
            
            // Then
            ArgumentCaptor<AuditLog> auditLogCaptor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(auditLogCaptor.capture());
            
            AuditLog auditLog = auditLogCaptor.getValue();
            assertThat(auditLog.getFromStatus()).isNull();
            assertThat(auditLog.getToStatus()).isEqualTo("WATCH");
            assertThat(auditLog.getReason()).isEqualTo("新建卡片");
        }
    }
}