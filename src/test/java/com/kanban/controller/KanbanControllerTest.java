package com.kanban.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kanban.domain.entity.Card;
import com.kanban.dto.*;
import com.kanban.service.KanbanService;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(KanbanController.class)
@DisplayName("看板控制器測試")
public class KanbanControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @MockBean
    private KanbanService kanbanService;
    
    private CardDto testCardDto;
    private PagedResponse<CardDto> testPagedResponse;
    
    @BeforeEach
    void setUp() {
        testCardDto = CardDto.builder()
                .id("card1")
                .stockCode("2330")
                .stockName("台積電")
                .status(Card.CardStatus.WATCH)
                .note("測試備註")
                .currentPrice(new BigDecimal("580.00"))
                .changePercent(new BigDecimal("2.5"))
                .volume(25000000L)
                .ma20(new BigDecimal("575.00"))
                .rsi(new BigDecimal("65.5"))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        PagedResponse.PaginationInfo paginationInfo = PagedResponse.PaginationInfo.builder()
                .page(0)
                .size(50)
                .totalElements(1L)
                .totalPages(1)
                .hasNext(false)
                .hasPrevious(false)
                .build();
        
        PagedResponse.MetaInfo metaInfo = PagedResponse.MetaInfo.builder()
                .timestamp(Instant.now())
                .traceId("test-trace-id")
                .version("1.0.0")
                .build();
        
        testPagedResponse = PagedResponse.<CardDto>builder()
                .success(true)
                .data(List.of(testCardDto))
                .pagination(paginationInfo)
                .meta(metaInfo)
                .build();
    }
    
    @Nested
    @DisplayName("分頁與搜尋功能測試")
    class PaginationAndSearchTests {
        
        @Test
        @WithMockUser(username = "testuser")
        @DisplayName("應該返回分頁卡片列表")
        void getCards_ShouldReturnPagedCardList() throws Exception {
            // Given
            when(kanbanService.getCards(eq("testuser"), any(CardSearchRequest.class)))
                    .thenReturn(testPagedResponse);
            
            // When & Then
            mockMvc.perform(get("/api/kanban/cards")
                            .param("page", "0")
                            .param("size", "50")
                            .param("sort", "updatedAt")
                            .param("direction", "DESC"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[0].id").value("card1"))
                    .andExpect(jsonPath("$.data[0].stockCode").value("2330"))
                    .andExpect(jsonPath("$.data[0].stockName").value("台積電"))
                    .andExpect(jsonPath("$.data[0].status").value("WATCH"))
                    .andExpect(jsonPath("$.data[0].currentPrice").value(580.00))
                    .andExpect(jsonPath("$.pagination.page").value(0))
                    .andExpect(jsonPath("$.pagination.size").value(50))
                    .andExpect(jsonPath("$.pagination.totalElements").value(1))
                    .andExpect(jsonPath("$.pagination.totalPages").value(1))
                    .andExpect(jsonPath("$.meta.traceId").exists())
                    .andExpect(jsonPath("$.meta.version").value("1.0.0"));
            
            verify(kanbanService).getCards(eq("testuser"), argThat(request ->
                    request.getPage() == 0 &&
                    request.getSize() == 50 &&
                    request.getSortBy().equals("updatedAt") &&
                    request.getSortDirection().equals("DESC")
            ));
        }
        
        @Test
        @WithMockUser(username = "testuser")
        @DisplayName("應該支援查詢參數搜尋")
        void getCards_WithQueryParameter_ShouldReturnFilteredResults() throws Exception {
            // Given
            when(kanbanService.getCards(eq("testuser"), any(CardSearchRequest.class)))
                    .thenReturn(testPagedResponse);
            
            // When & Then
            mockMvc.perform(get("/api/kanban/cards")
                            .param("q", "台積電")
                            .param("page", "0")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray());
            
            verify(kanbanService).getCards(eq("testuser"), argThat(request ->
                    request.getQuery().equals("台積電") &&
                    request.getPage() == 0 &&
                    request.getSize() == 20
            ));
        }
        
        @Test
        @WithMockUser(username = "testuser")
        @DisplayName("應該支援狀態篩選")
        void getCards_WithStatusFilter_ShouldReturnFilteredResults() throws Exception {
            // Given
            when(kanbanService.getCards(eq("testuser"), any(CardSearchRequest.class)))
                    .thenReturn(testPagedResponse);
            
            // When & Then
            mockMvc.perform(get("/api/kanban/cards")
                            .param("status", "WATCH")
                            .param("page", "0")
                            .param("size", "50"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
            
            verify(kanbanService).getCards(eq("testuser"), argThat(request ->
                    request.getStatus() == Card.CardStatus.WATCH
            ));
        }
        
        @Test
        @WithMockUser(username = "testuser")
        @DisplayName("應該支援複合搜尋條件")
        void getCards_WithMultipleFilters_ShouldReturnFilteredResults() throws Exception {
            // Given
            when(kanbanService.getCards(eq("testuser"), any(CardSearchRequest.class)))
                    .thenReturn(testPagedResponse);
            
            // When & Then
            mockMvc.perform(get("/api/kanban/cards")
                            .param("q", "台積電")
                            .param("status", "READY_TO_BUY")
                            .param("sort", "stockCode")
                            .param("direction", "ASC")
                            .param("page", "1")
                            .param("size", "25"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
            
            verify(kanbanService).getCards(eq("testuser"), argThat(request ->
                    request.getQuery().equals("台積電") &&
                    request.getStatus() == Card.CardStatus.READY_TO_BUY &&
                    request.getSortBy().equals("stockCode") &&
                    request.getSortDirection().equals("ASC") &&
                    request.getPage() == 1 &&
                    request.getSize() == 25
            ));
        }
        
        @Test
        @WithMockUser(username = "testuser")
        @DisplayName("應該限制最大頁面大小")
        void getCards_WithLargePageSize_ShouldLimitToMaximum() throws Exception {
            // Given
            when(kanbanService.getCards(eq("testuser"), any(CardSearchRequest.class)))
                    .thenReturn(testPagedResponse);
            
            // When & Then
            mockMvc.perform(get("/api/kanban/cards")
                            .param("size", "200")) // 超過最大限制 100
                    .andExpect(status().isOk());
            
            verify(kanbanService).getCards(eq("testuser"), argThat(request ->
                    request.getSize() == 100 // 應該被限制為 100
            ));
        }
        
        @Test
        @WithMockUser(username = "testuser")
        @DisplayName("應該支援不同排序欄位")
        void getCards_WithDifferentSortFields_ShouldApplySorting() throws Exception {
            // Given
            when(kanbanService.getCards(eq("testuser"), any(CardSearchRequest.class)))
                    .thenReturn(testPagedResponse);
            
            // Test different sort fields
            String[] sortFields = {"stockCode", "stockName", "status", "createdAt", "updatedAt"};
            String[] directions = {"ASC", "DESC"};
            
            for (String sortField : sortFields) {
                for (String direction : directions) {
                    // When & Then
                    mockMvc.perform(get("/api/kanban/cards")
                                    .param("sort", sortField)
                                    .param("direction", direction))
                            .andExpect(status().isOk());
                    
                    verify(kanbanService).getCards(eq("testuser"), argThat(request ->
                            request.getSortBy().equals(sortField) &&
                            request.getSortDirection().equals(direction)
                    ));
                }
            }
        }
    }
    
    @Nested
    @DisplayName("卡片狀態流轉邏輯測試")
    class CardStatusTransitionTests {
        
        @Test
        @WithMockUser(username = "testuser")
        @DisplayName("應該成功更新卡片狀態")
        void updateCard_ShouldUpdateCardStatus() throws Exception {
            // Given
            CardUpdateRequest updateRequest = CardUpdateRequest.builder()
                    .status(Card.CardStatus.READY_TO_BUY)
                    .reason("手動移動")
                    .build();
            
            CardDto updatedCard = testCardDto.toBuilder()
                    .status(Card.CardStatus.READY_TO_BUY)
                    .build();
            
            when(kanbanService.updateCard(eq("testuser"), eq("card1"), any(CardUpdateRequest.class)))
                    .thenReturn(updatedCard);
            
            // When & Then
            mockMvc.perform(patch("/api/kanban/cards/card1")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value("card1"))
                    .andExpect(jsonPath("$.status").value("READY_TO_BUY"));
            
            verify(kanbanService).updateCard(eq("testuser"), eq("card1"), argThat(request ->
                    request.getStatus() == Card.CardStatus.READY_TO_BUY &&
                    request.getReason().equals("手動移動")
            ));
        }
        
        @Test
        @WithMockUser(username = "testuser")
        @DisplayName("應該支援所有狀態轉換")
        void updateCard_ShouldSupportAllStatusTransitions() throws Exception {
            // Given
            Card.CardStatus[] allStatuses = {
                Card.CardStatus.WATCH,
                Card.CardStatus.READY_TO_BUY,
                Card.CardStatus.HOLD,
                Card.CardStatus.SELL,
                Card.CardStatus.ALERTS
            };
            
            for (Card.CardStatus targetStatus : allStatuses) {
                CardUpdateRequest updateRequest = CardUpdateRequest.builder()
                        .status(targetStatus)
                        .reason("狀態轉換測試")
                        .build();
                
                CardDto updatedCard = testCardDto.toBuilder()
                        .status(targetStatus)
                        .build();
                
                when(kanbanService.updateCard(eq("testuser"), eq("card1"), any(CardUpdateRequest.class)))
                        .thenReturn(updatedCard);
                
                // When & Then
                mockMvc.perform(patch("/api/kanban/cards/card1")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.status").value(targetStatus.name()));
            }
        }
        
        @Test
        @WithMockUser(username = "testuser")
        @DisplayName("應該支援只更新備註")
        void updateCard_OnlyNote_ShouldUpdateNote() throws Exception {
            // Given
            CardUpdateRequest updateRequest = CardUpdateRequest.builder()
                    .note("更新後的備註")
                    .build();
            
            CardDto updatedCard = testCardDto.toBuilder()
                    .note("更新後的備註")
                    .build();
            
            when(kanbanService.updateCard(eq("testuser"), eq("card1"), any(CardUpdateRequest.class)))
                    .thenReturn(updatedCard);
            
            // When & Then
            mockMvc.perform(patch("/api/kanban/cards/card1")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.note").value("更新後的備註"));
            
            verify(kanbanService).updateCard(eq("testuser"), eq("card1"), argThat(request ->
                    request.getStatus() == null &&
                    request.getNote().equals("更新後的備註")
            ));
        }
        
        @Test
        @WithMockUser(username = "testuser")
        @DisplayName("應該支援同時更新狀態和備註")
        void updateCard_StatusAndNote_ShouldUpdateBoth() throws Exception {
            // Given
            CardUpdateRequest updateRequest = CardUpdateRequest.builder()
                    .status(Card.CardStatus.HOLD)
                    .note("已買進持有")
                    .reason("執行買進策略")
                    .build();
            
            CardDto updatedCard = testCardDto.toBuilder()
                    .status(Card.CardStatus.HOLD)
                    .note("已買進持有")
                    .build();
            
            when(kanbanService.updateCard(eq("testuser"), eq("card1"), any(CardUpdateRequest.class)))
                    .thenReturn(updatedCard);
            
            // When & Then
            mockMvc.perform(patch("/api/kanban/cards/card1")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("HOLD"))
                    .andExpect(jsonPath("$.note").value("已買進持有"));
            
            verify(kanbanService).updateCard(eq("testuser"), eq("card1"), argThat(request ->
                    request.getStatus() == Card.CardStatus.HOLD &&
                    request.getNote().equals("已買進持有") &&
                    request.getReason().equals("執行買進策略")
            ));
        }
        
        @Test
        @WithMockUser(username = "testuser")
        @DisplayName("應該驗證備註長度限制")
        void updateCard_WithLongNote_ShouldReturnValidationError() throws Exception {
            // Given
            String longNote = "a".repeat(1001); // 超過 1000 字元限制
            CardUpdateRequest updateRequest = CardUpdateRequest.builder()
                    .note(longNote)
                    .build();
            
            // When & Then
            mockMvc.perform(patch("/api/kanban/cards/card1")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().isBadRequest());
            
            verify(kanbanService, never()).updateCard(anyString(), anyString(), any());
        }
    }
    
    @Nested
    @DisplayName("卡片查詢功能測試")
    class CardRetrievalTests {
        
        @Test
        @WithMockUser(username = "testuser")
        @DisplayName("應該返回單一卡片詳情")
        void getCard_ShouldReturnCardDetails() throws Exception {
            // Given
            when(kanbanService.getCard("testuser", "card1")).thenReturn(testCardDto);
            
            // When & Then
            mockMvc.perform(get("/api/kanban/cards/card1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value("card1"))
                    .andExpect(jsonPath("$.stockCode").value("2330"))
                    .andExpect(jsonPath("$.stockName").value("台積電"))
                    .andExpect(jsonPath("$.status").value("WATCH"))
                    .andExpect(jsonPath("$.currentPrice").value(580.00))
                    .andExpect(jsonPath("$.changePercent").value(2.5))
                    .andExpect(jsonPath("$.volume").value(25000000))
                    .andExpect(jsonPath("$.ma20").value(575.00))
                    .andExpect(jsonPath("$.rsi").value(65.5));
            
            verify(kanbanService).getCard("testuser", "card1");
        }
        
        @Test
        @WithMockUser(username = "testuser")
        @DisplayName("應該返回看板統計資訊")
        void getKanbanStats_ShouldReturnStatistics() throws Exception {
            // Given
            when(kanbanService.getCardCount("testuser")).thenReturn(10L);
            when(kanbanService.getCardCountByStatus("testuser", Card.CardStatus.WATCH)).thenReturn(3L);
            when(kanbanService.getCardCountByStatus("testuser", Card.CardStatus.READY_TO_BUY)).thenReturn(2L);
            when(kanbanService.getCardCountByStatus("testuser", Card.CardStatus.HOLD)).thenReturn(3L);
            when(kanbanService.getCardCountByStatus("testuser", Card.CardStatus.SELL)).thenReturn(1L);
            when(kanbanService.getCardCountByStatus("testuser", Card.CardStatus.ALERTS)).thenReturn(1L);
            
            // When & Then
            mockMvc.perform(get("/api/kanban/stats"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalCards").value(10))
                    .andExpect(jsonPath("$.watchCount").value(3))
                    .andExpect(jsonPath("$.readyToBuyCount").value(2))
                    .andExpect(jsonPath("$.holdCount").value(3))
                    .andExpect(jsonPath("$.sellCount").value(1))
                    .andExpect(jsonPath("$.alertsCount").value(1));
            
            verify(kanbanService).getCardCount("testuser");
            verify(kanbanService).getCardCountByStatus("testuser", Card.CardStatus.WATCH);
            verify(kanbanService).getCardCountByStatus("testuser", Card.CardStatus.READY_TO_BUY);
            verify(kanbanService).getCardCountByStatus("testuser", Card.CardStatus.HOLD);
            verify(kanbanService).getCardCountByStatus("testuser", Card.CardStatus.SELL);
            verify(kanbanService).getCardCountByStatus("testuser", Card.CardStatus.ALERTS);
        }
    }
    
    @Nested
    @DisplayName("安全性與權限測試")
    class SecurityTests {
        
        @Test
        @DisplayName("未認證使用者應該被拒絕存取")
        void getCards_WithoutAuthentication_ShouldReturnUnauthorized() throws Exception {
            mockMvc.perform(get("/api/kanban/cards"))
                    .andExpect(status().isUnauthorized());
            
            verify(kanbanService, never()).getCards(anyString(), any());
        }
        
        @Test
        @DisplayName("更新卡片需要 CSRF 保護")
        void updateCard_WithoutCSRF_ShouldReturnForbidden() throws Exception {
            CardUpdateRequest updateRequest = CardUpdateRequest.builder()
                    .status(Card.CardStatus.READY_TO_BUY)
                    .build();
            
            mockMvc.perform(patch("/api/kanban/cards/card1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().isForbidden());
            
            verify(kanbanService, never()).updateCard(anyString(), anyString(), any());
        }
    }
}