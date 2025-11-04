package com.kanban.service;

import com.kanban.domain.entity.Card;
import com.kanban.domain.entity.StockSnapshot;
import com.kanban.domain.entity.User;
import com.kanban.domain.entity.Watchlist;
import com.kanban.dto.CardDto;
import com.kanban.dto.CardSearchRequest;
import com.kanban.dto.PagedResponse;
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
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("看板分頁與搜尋功能測試")
public class KanbanPaginationSearchTest {
    
    @Mock
    private CardRepository cardRepository;
    
    @Mock
    private StockDataService stockDataService;
    
    @Mock
    private AuditLogService auditLogService;
    
    @InjectMocks
    private KanbanService kanbanService;
    
    private User testUser;
    private Watchlist testWatchlist;
    private List<Card> testCards;
    private List<StockSnapshot> testSnapshots;
    
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
        
        // 建立測試卡片資料
        testCards = Arrays.asList(
                createTestCard("card1", "2330", "台積電", Card.CardStatus.WATCH, "半導體龍頭"),
                createTestCard("card2", "2317", "鴻海", Card.CardStatus.READY_TO_BUY, "代工大廠"),
                createTestCard("card3", "2454", "聯發科", Card.CardStatus.HOLD, "IC設計"),
                createTestCard("card4", "2412", "中華電", Card.CardStatus.SELL, "電信股"),
                createTestCard("card5", "1301", "台塑", Card.CardStatus.ALERTS, "石化股")
        );
        
        // 建立對應的股票快照資料
        testSnapshots = Arrays.asList(
                createTestSnapshot("2330", "台積電", new BigDecimal("580.00"), new BigDecimal("2.5")),
                createTestSnapshot("2317", "鴻海", new BigDecimal("105.50"), new BigDecimal("-1.2")),
                createTestSnapshot("2454", "聯發科", new BigDecimal("920.00"), new BigDecimal("3.8")),
                createTestSnapshot("2412", "中華電", new BigDecimal("123.00"), new BigDecimal("0.5")),
                createTestSnapshot("1301", "台塑", new BigDecimal("95.20"), new BigDecimal("-2.1"))
        );
    }
    
    @Nested
    @DisplayName("分頁功能測試")
    class PaginationTests {
        
        @Test
        @DisplayName("應該正確處理第一頁請求")
        void getCards_FirstPage_ShouldReturnCorrectPage() {
            // Given
            CardSearchRequest request = CardSearchRequest.builder()
                    .page(0)
                    .size(3)
                    .sortBy("updatedAt")
                    .sortDirection("DESC")
                    .build();
            
            List<Card> firstPageCards = testCards.subList(0, 3);
            Page<Card> cardPage = new PageImpl<>(firstPageCards, request.toPageable(), testCards.size());
            
            when(cardRepository.findByUserIdAndStatusNot(eq("user1"), eq(Card.CardStatus.ARCHIVED), any(Pageable.class)))
                    .thenReturn(cardPage);
            
            // Mock stock data for each card
            firstPageCards.forEach(card -> 
                when(stockDataService.getSnapshot(card.getStockCode()))
                    .thenReturn(getSnapshotForCode(card.getStockCode()))
            );
            
            // When
            PagedResponse<CardDto> result = kanbanService.getCards("user1", request);
            
            // Then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData()).hasSize(3);
            assertThat(result.getPagination().getPage()).isEqualTo(0);
            assertThat(result.getPagination().getSize()).isEqualTo(3);
            assertThat(result.getPagination().getTotalElements()).isEqualTo(5);
            assertThat(result.getPagination().getTotalPages()).isEqualTo(2);
            assertThat(result.getPagination().isHasNext()).isTrue();
            assertThat(result.getPagination().isHasPrevious()).isFalse();
        }
        
        @Test
        @DisplayName("應該正確處理最後一頁請求")
        void getCards_LastPage_ShouldReturnCorrectPage() {
            // Given
            CardSearchRequest request = CardSearchRequest.builder()
                    .page(1)
                    .size(3)
                    .sortBy("updatedAt")
                    .sortDirection("DESC")
                    .build();
            
            List<Card> lastPageCards = testCards.subList(3, 5); // 最後 2 張卡片
            Page<Card> cardPage = new PageImpl<>(lastPageCards, request.toPageable(), testCards.size());
            
            when(cardRepository.findByUserIdAndStatusNot(eq("user1"), eq(Card.CardStatus.ARCHIVED), any(Pageable.class)))
                    .thenReturn(cardPage);
            
            // Mock stock data
            lastPageCards.forEach(card -> 
                when(stockDataService.getSnapshot(card.getStockCode()))
                    .thenReturn(getSnapshotForCode(card.getStockCode()))
            );
            
            // When
            PagedResponse<CardDto> result = kanbanService.getCards("user1", request);
            
            // Then
            assertThat(result.getData()).hasSize(2);
            assertThat(result.getPagination().getPage()).isEqualTo(1);
            assertThat(result.getPagination().isHasNext()).isFalse();
            assertThat(result.getPagination().isHasPrevious()).isTrue();
        }
        
        @Test
        @DisplayName("應該正確處理空頁面請求")
        void getCards_EmptyPage_ShouldReturnEmptyResult() {
            // Given
            CardSearchRequest request = CardSearchRequest.builder()
                    .page(10) // 超出範圍的頁面
                    .size(50)
                    .build();
            
            Page<Card> emptyPage = new PageImpl<>(List.of(), request.toPageable(), testCards.size());
            
            when(cardRepository.findByUserIdAndStatusNot(eq("user1"), eq(Card.CardStatus.ARCHIVED), any(Pageable.class)))
                    .thenReturn(emptyPage);
            
            // When
            PagedResponse<CardDto> result = kanbanService.getCards("user1", request);
            
            // Then
            assertThat(result.getData()).isEmpty();
            assertThat(result.getPagination().getPage()).isEqualTo(10);
            assertThat(result.getPagination().getTotalElements()).isEqualTo(5);
            assertThat(result.getPagination().isHasNext()).isFalse();
            assertThat(result.getPagination().isHasPrevious()).isTrue();
        }
        
        @Test
        @DisplayName("應該驗證分頁參數並使用正確的 Pageable")
        void getCards_ShouldCreateCorrectPageable() {
            // Given
            CardSearchRequest request = CardSearchRequest.builder()
                    .page(2)
                    .size(25)
                    .sortBy("stockCode")
                    .sortDirection("ASC")
                    .build();
            
            Page<Card> cardPage = new PageImpl<>(List.of(), request.toPageable(), 0);
            when(cardRepository.findByUserIdAndStatusNot(eq("user1"), eq(Card.CardStatus.ARCHIVED), any(Pageable.class)))
                    .thenReturn(cardPage);
            
            // When
            kanbanService.getCards("user1", request);
            
            // Then
            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            verify(cardRepository).findByUserIdAndStatusNot(eq("user1"), eq(Card.CardStatus.ARCHIVED), pageableCaptor.capture());
            
            Pageable capturedPageable = pageableCaptor.getValue();
            assertThat(capturedPageable.getPageNumber()).isEqualTo(2);
            assertThat(capturedPageable.getPageSize()).isEqualTo(25);
            assertThat(capturedPageable.getSort().getOrderFor("stockCode")).isNotNull();
            assertThat(capturedPageable.getSort().getOrderFor("stockCode").getDirection()).isEqualTo(Sort.Direction.ASC);
        }
    }
    
    @Nested
    @DisplayName("搜尋功能測試")
    class SearchTests {
        
        @Test
        @DisplayName("應該支援股票代碼搜尋")
        void getCards_SearchByStockCode_ShouldReturnMatchingCards() {
            // Given
            CardSearchRequest request = CardSearchRequest.builder()
                    .query("2330")
                    .page(0)
                    .size(50)
                    .build();
            
            List<Card> matchingCards = testCards.stream()
                    .filter(card -> card.getStockCode().contains("2330"))
                    .toList();
            
            Page<Card> cardPage = new PageImpl<>(matchingCards, request.toPageable(), matchingCards.size());
            
            when(cardRepository.findByUserIdAndQuery(eq("user1"), eq("2330"), any(Pageable.class)))
                    .thenReturn(cardPage);
            when(stockDataService.getSnapshot("2330")).thenReturn(getSnapshotForCode("2330"));
            
            // When
            PagedResponse<CardDto> result = kanbanService.getCards("user1", request);
            
            // Then
            assertThat(result.getData()).hasSize(1);
            assertThat(result.getData().get(0).getStockCode()).isEqualTo("2330");
            assertThat(result.getData().get(0).getStockName()).isEqualTo("台積電");
            
            verify(cardRepository).findByUserIdAndQuery(eq("user1"), eq("2330"), any(Pageable.class));
        }
        
        @Test
        @DisplayName("應該支援股票名稱搜尋")
        void getCards_SearchByStockName_ShouldReturnMatchingCards() {
            // Given
            CardSearchRequest request = CardSearchRequest.builder()
                    .query("台積電")
                    .page(0)
                    .size(50)
                    .build();
            
            List<Card> matchingCards = testCards.stream()
                    .filter(card -> card.getStockName().contains("台積電"))
                    .toList();
            
            Page<Card> cardPage = new PageImpl<>(matchingCards, request.toPageable(), matchingCards.size());
            
            when(cardRepository.findByUserIdAndQuery(eq("user1"), eq("台積電"), any(Pageable.class)))
                    .thenReturn(cardPage);
            when(stockDataService.getSnapshot("2330")).thenReturn(getSnapshotForCode("2330"));
            
            // When
            PagedResponse<CardDto> result = kanbanService.getCards("user1", request);
            
            // Then
            assertThat(result.getData()).hasSize(1);
            assertThat(result.getData().get(0).getStockName()).contains("台積電");
            
            verify(cardRepository).findByUserIdAndQuery(eq("user1"), eq("台積電"), any(Pageable.class));
        }
        
        @Test
        @DisplayName("應該支援備註內容搜尋")
        void getCards_SearchByNote_ShouldReturnMatchingCards() {
            // Given
            CardSearchRequest request = CardSearchRequest.builder()
                    .query("半導體")
                    .page(0)
                    .size(50)
                    .build();
            
            List<Card> matchingCards = testCards.stream()
                    .filter(card -> card.getNote() != null && card.getNote().contains("半導體"))
                    .toList();
            
            Page<Card> cardPage = new PageImpl<>(matchingCards, request.toPageable(), matchingCards.size());
            
            when(cardRepository.findByUserIdAndQuery(eq("user1"), eq("半導體"), any(Pageable.class)))
                    .thenReturn(cardPage);
            when(stockDataService.getSnapshot("2330")).thenReturn(getSnapshotForCode("2330"));
            
            // When
            PagedResponse<CardDto> result = kanbanService.getCards("user1", request);
            
            // Then
            assertThat(result.getData()).hasSize(1);
            assertThat(result.getData().get(0).getNote()).contains("半導體");
            
            verify(cardRepository).findByUserIdAndQuery(eq("user1"), eq("半導體"), any(Pageable.class));
        }
        
        @Test
        @DisplayName("應該處理空搜尋結果")
        void getCards_SearchWithNoResults_ShouldReturnEmptyList() {
            // Given
            CardSearchRequest request = CardSearchRequest.builder()
                    .query("不存在的股票")
                    .page(0)
                    .size(50)
                    .build();
            
            Page<Card> emptyPage = new PageImpl<>(List.of(), request.toPageable(), 0);
            
            when(cardRepository.findByUserIdAndQuery(eq("user1"), eq("不存在的股票"), any(Pageable.class)))
                    .thenReturn(emptyPage);
            
            // When
            PagedResponse<CardDto> result = kanbanService.getCards("user1", request);
            
            // Then
            assertThat(result.getData()).isEmpty();
            assertThat(result.getPagination().getTotalElements()).isEqualTo(0);
            
            verify(cardRepository).findByUserIdAndQuery(eq("user1"), eq("不存在的股票"), any(Pageable.class));
        }
        
        @Test
        @DisplayName("應該忽略空白搜尋查詢")
        void getCards_WithEmptyQuery_ShouldIgnoreQuery() {
            // Given
            CardSearchRequest request = CardSearchRequest.builder()
                    .query("   ") // 只有空白
                    .page(0)
                    .size(50)
                    .build();
            
            Page<Card> cardPage = new PageImpl<>(testCards, request.toPageable(), testCards.size());
            
            when(cardRepository.findByUserIdAndStatusNot(eq("user1"), eq(Card.CardStatus.ARCHIVED), any(Pageable.class)))
                    .thenReturn(cardPage);
            
            // Mock all stock data
            testCards.forEach(card -> 
                when(stockDataService.getSnapshot(card.getStockCode()))
                    .thenReturn(getSnapshotForCode(card.getStockCode()))
            );
            
            // When
            PagedResponse<CardDto> result = kanbanService.getCards("user1", request);
            
            // Then
            assertThat(result.getData()).hasSize(5); // 返回所有卡片
            
            // 應該呼叫無查詢的方法，而不是有查詢的方法
            verify(cardRepository).findByUserIdAndStatusNot(eq("user1"), eq(Card.CardStatus.ARCHIVED), any(Pageable.class));
            verify(cardRepository, never()).findByUserIdAndQuery(anyString(), anyString(), any(Pageable.class));
        }
    }
    
    @Nested
    @DisplayName("狀態篩選測試")
    class StatusFilterTests {
        
        @Test
        @DisplayName("應該支援單一狀態篩選")
        void getCards_FilterByStatus_ShouldReturnMatchingCards() {
            // Given
            CardSearchRequest request = CardSearchRequest.builder()
                    .status(Card.CardStatus.WATCH)
                    .page(0)
                    .size(50)
                    .build();
            
            List<Card> watchCards = testCards.stream()
                    .filter(card -> card.getStatus() == Card.CardStatus.WATCH)
                    .toList();
            
            Page<Card> cardPage = new PageImpl<>(watchCards, request.toPageable(), watchCards.size());
            
            when(cardRepository.findByUserIdAndStatus(eq("user1"), eq(Card.CardStatus.WATCH), any(Pageable.class)))
                    .thenReturn(cardPage);
            when(stockDataService.getSnapshot("2330")).thenReturn(getSnapshotForCode("2330"));
            
            // When
            PagedResponse<CardDto> result = kanbanService.getCards("user1", request);
            
            // Then
            assertThat(result.getData()).hasSize(1);
            assertThat(result.getData().get(0).getStatus()).isEqualTo(Card.CardStatus.WATCH);
            
            verify(cardRepository).findByUserIdAndStatus(eq("user1"), eq(Card.CardStatus.WATCH), any(Pageable.class));
        }
        
        @Test
        @DisplayName("應該支援所有狀態的篩選")
        void getCards_FilterByAllStatuses_ShouldWork() {
            // Given
            Card.CardStatus[] allStatuses = {
                Card.CardStatus.WATCH,
                Card.CardStatus.READY_TO_BUY,
                Card.CardStatus.HOLD,
                Card.CardStatus.SELL,
                Card.CardStatus.ALERTS
            };
            
            for (Card.CardStatus status : allStatuses) {
                CardSearchRequest request = CardSearchRequest.builder()
                        .status(status)
                        .page(0)
                        .size(50)
                        .build();
                
                List<Card> statusCards = testCards.stream()
                        .filter(card -> card.getStatus() == status)
                        .toList();
                
                Page<Card> cardPage = new PageImpl<>(statusCards, request.toPageable(), statusCards.size());
                
                when(cardRepository.findByUserIdAndStatus(eq("user1"), eq(status), any(Pageable.class)))
                        .thenReturn(cardPage);
                
                // Mock stock data for matching cards
                statusCards.forEach(card -> 
                    when(stockDataService.getSnapshot(card.getStockCode()))
                        .thenReturn(getSnapshotForCode(card.getStockCode()))
                );
                
                // When
                PagedResponse<CardDto> result = kanbanService.getCards("user1", request);
                
                // Then
                assertThat(result.getData()).allMatch(cardDto -> cardDto.getStatus() == status);
                verify(cardRepository).findByUserIdAndStatus(eq("user1"), eq(status), any(Pageable.class));
            }
        }
    }
    
    @Nested
    @DisplayName("複合搜尋測試")
    class CombinedSearchTests {
        
        @Test
        @DisplayName("應該支援查詢和狀態的複合篩選")
        void getCards_CombinedQueryAndStatus_ShouldReturnMatchingCards() {
            // Given
            CardSearchRequest request = CardSearchRequest.builder()
                    .query("2330")
                    .status(Card.CardStatus.WATCH)
                    .page(0)
                    .size(50)
                    .build();
            
            List<Card> matchingCards = testCards.stream()
                    .filter(card -> card.getStockCode().contains("2330") && card.getStatus() == Card.CardStatus.WATCH)
                    .toList();
            
            Page<Card> cardPage = new PageImpl<>(matchingCards, request.toPageable(), matchingCards.size());
            
            when(cardRepository.findByUserIdAndStatusAndQuery(eq("user1"), eq(Card.CardStatus.WATCH), eq("2330"), any(Pageable.class)))
                    .thenReturn(cardPage);
            when(stockDataService.getSnapshot("2330")).thenReturn(getSnapshotForCode("2330"));
            
            // When
            PagedResponse<CardDto> result = kanbanService.getCards("user1", request);
            
            // Then
            assertThat(result.getData()).hasSize(1);
            assertThat(result.getData().get(0).getStockCode()).isEqualTo("2330");
            assertThat(result.getData().get(0).getStatus()).isEqualTo(Card.CardStatus.WATCH);
            
            verify(cardRepository).findByUserIdAndStatusAndQuery(eq("user1"), eq(Card.CardStatus.WATCH), eq("2330"), any(Pageable.class));
        }
        
        @Test
        @DisplayName("複合搜尋無結果時應該返回空列表")
        void getCards_CombinedSearchNoResults_ShouldReturnEmptyList() {
            // Given
            CardSearchRequest request = CardSearchRequest.builder()
                    .query("2330")
                    .status(Card.CardStatus.SELL) // 2330 不在 SELL 狀態
                    .page(0)
                    .size(50)
                    .build();
            
            Page<Card> emptyPage = new PageImpl<>(List.of(), request.toPageable(), 0);
            
            when(cardRepository.findByUserIdAndStatusAndQuery(eq("user1"), eq(Card.CardStatus.SELL), eq("2330"), any(Pageable.class)))
                    .thenReturn(emptyPage);
            
            // When
            PagedResponse<CardDto> result = kanbanService.getCards("user1", request);
            
            // Then
            assertThat(result.getData()).isEmpty();
            assertThat(result.getPagination().getTotalElements()).isEqualTo(0);
        }
    }
    
    @Nested
    @DisplayName("排序功能測試")
    class SortingTests {
        
        @Test
        @DisplayName("應該支援按更新時間排序")
        void getCards_SortByUpdatedAt_ShouldApplyCorrectSort() {
            // Given
            CardSearchRequest request = CardSearchRequest.builder()
                    .sortBy("updatedAt")
                    .sortDirection("DESC")
                    .page(0)
                    .size(50)
                    .build();
            
            Page<Card> cardPage = new PageImpl<>(testCards, request.toPageable(), testCards.size());
            when(cardRepository.findByUserIdAndStatusNot(eq("user1"), eq(Card.CardStatus.ARCHIVED), any(Pageable.class)))
                    .thenReturn(cardPage);
            
            // Mock stock data
            testCards.forEach(card -> 
                when(stockDataService.getSnapshot(card.getStockCode()))
                    .thenReturn(getSnapshotForCode(card.getStockCode()))
            );
            
            // When
            kanbanService.getCards("user1", request);
            
            // Then
            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            verify(cardRepository).findByUserIdAndStatusNot(eq("user1"), eq(Card.CardStatus.ARCHIVED), pageableCaptor.capture());
            
            Pageable pageable = pageableCaptor.getValue();
            assertThat(pageable.getSort().getOrderFor("updatedAt")).isNotNull();
            assertThat(pageable.getSort().getOrderFor("updatedAt").getDirection()).isEqualTo(Sort.Direction.DESC);
        }
        
        @Test
        @DisplayName("應該支援所有有效的排序欄位")
        void getCards_AllValidSortFields_ShouldWork() {
            // Given
            String[] validSortFields = {"stockCode", "stockName", "status", "createdAt", "updatedAt"};
            String[] directions = {"ASC", "DESC"};
            
            for (String sortField : validSortFields) {
                for (String direction : directions) {
                    CardSearchRequest request = CardSearchRequest.builder()
                            .sortBy(sortField)
                            .sortDirection(direction)
                            .page(0)
                            .size(50)
                            .build();
                    
                    Page<Card> cardPage = new PageImpl<>(testCards, request.toPageable(), testCards.size());
                    when(cardRepository.findByUserIdAndStatusNot(eq("user1"), eq(Card.CardStatus.ARCHIVED), any(Pageable.class)))
                            .thenReturn(cardPage);
                    
                    // When
                    kanbanService.getCards("user1", request);
                    
                    // Then
                    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
                    verify(cardRepository).findByUserIdAndStatusNot(eq("user1"), eq(Card.CardStatus.ARCHIVED), pageableCaptor.capture());
                    
                    Pageable pageable = pageableCaptor.getValue();
                    assertThat(pageable.getSort().getOrderFor(sortField)).isNotNull();
                    assertThat(pageable.getSort().getOrderFor(sortField).getDirection())
                            .isEqualTo(Sort.Direction.valueOf(direction));
                }
            }
        }
        
        @Test
        @DisplayName("無效排序欄位應該使用預設排序")
        void getCards_InvalidSortField_ShouldUseDefaultSort() {
            // Given
            CardSearchRequest request = CardSearchRequest.builder()
                    .sortBy("invalidField")
                    .sortDirection("ASC")
                    .page(0)
                    .size(50)
                    .build();
            
            Page<Card> cardPage = new PageImpl<>(testCards, request.toPageable(), testCards.size());
            when(cardRepository.findByUserIdAndStatusNot(eq("user1"), eq(Card.CardStatus.ARCHIVED), any(Pageable.class)))
                    .thenReturn(cardPage);
            
            // When
            kanbanService.getCards("user1", request);
            
            // Then
            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            verify(cardRepository).findByUserIdAndStatusNot(eq("user1"), eq(Card.CardStatus.ARCHIVED), pageableCaptor.capture());
            
            Pageable pageable = pageableCaptor.getValue();
            // 應該使用預設的 updatedAt 排序
            assertThat(pageable.getSort().getOrderFor("updatedAt")).isNotNull();
            assertThat(pageable.getSort().getOrderFor("updatedAt").getDirection()).isEqualTo(Sort.Direction.ASC);
        }
    }
    
    // Helper methods
    private Card createTestCard(String id, String stockCode, String stockName, Card.CardStatus status, String note) {
        return Card.builder()
                .id(id)
                .user(testUser)
                .watchlist(testWatchlist)
                .stockCode(stockCode)
                .stockName(stockName)
                .status(status)
                .note(note)
                .createdAt(LocalDateTime.now().minusDays(1))
                .updatedAt(LocalDateTime.now())
                .build();
    }
    
    private StockSnapshot createTestSnapshot(String code, String name, BigDecimal price, BigDecimal changePercent) {
        return StockSnapshot.builder()
                .code(code)
                .name(name)
                .currentPrice(price)
                .changePercent(changePercent)
                .volume(1000000L)
                .ma20(price.subtract(BigDecimal.TEN))
                .rsi(new BigDecimal("65.0"))
                .build();
    }
    
    private StockSnapshot getSnapshotForCode(String stockCode) {
        return testSnapshots.stream()
                .filter(snapshot -> snapshot.getCode().equals(stockCode))
                .findFirst()
                .orElse(null);
    }
}