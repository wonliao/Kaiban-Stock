package com.kanban.service;

import com.kanban.domain.entity.Card;
import com.kanban.domain.entity.StockSnapshot;
import com.kanban.domain.entity.User;
import com.kanban.dto.CardDto;
import com.kanban.dto.CardSearchRequest;
import com.kanban.dto.CardUpdateRequest;
import com.kanban.dto.PagedResponse;
import com.kanban.exception.StockNotFoundException;
import com.kanban.repository.CardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KanbanServiceTest {
    
    @Mock
    private CardRepository cardRepository;
    
    @Mock
    private StockDataService stockDataService;
    
    @Mock
    private AuditLogService auditLogService;
    
    @InjectMocks
    private KanbanService kanbanService;
    
    private User testUser;
    private Card testCard;
    private StockSnapshot testSnapshot;
    
    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id("user1")
                .username("testuser")
                .email("test@example.com")
                .build();
        
        testCard = Card.builder()
                .id("card1")
                .user(testUser)
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
    
    @Test
    void getCards_ShouldReturnPagedResponse() {
        // Given
        CardSearchRequest request = CardSearchRequest.builder()
                .page(0)
                .size(50)
                .sortBy("updatedAt")
                .sortDirection("DESC")
                .build();
        
        Page<Card> cardPage = new PageImpl<>(List.of(testCard));
        when(cardRepository.findByUserIdAndStatusNot(eq("user1"), eq(Card.CardStatus.ARCHIVED), any(Pageable.class)))
                .thenReturn(cardPage);
        when(stockDataService.getSnapshot("2330")).thenReturn(testSnapshot);
        
        // When
        PagedResponse<CardDto> result = kanbanService.getCards("user1", request);
        
        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).hasSize(1);
        assertThat(result.getData().get(0).getStockCode()).isEqualTo("2330");
        assertThat(result.getData().get(0).getCurrentPrice()).isEqualTo(new BigDecimal("580.00"));
        assertThat(result.getPagination().getTotalElements()).isEqualTo(1);
    }
    
    @Test
    void getCards_WithQueryFilter_ShouldReturnFilteredResults() {
        // Given
        CardSearchRequest request = CardSearchRequest.builder()
                .query("台積電")
                .page(0)
                .size(50)
                .build();
        
        Page<Card> cardPage = new PageImpl<>(List.of(testCard));
        when(cardRepository.findByUserIdAndQuery(eq("user1"), eq("台積電"), any(Pageable.class)))
                .thenReturn(cardPage);
        when(stockDataService.getSnapshot("2330")).thenReturn(testSnapshot);
        
        // When
        PagedResponse<CardDto> result = kanbanService.getCards("user1", request);
        
        // Then
        assertThat(result.getData()).hasSize(1);
        verify(cardRepository).findByUserIdAndQuery(eq("user1"), eq("台積電"), any(Pageable.class));
    }
    
    @Test
    void getCards_WithStatusFilter_ShouldReturnFilteredResults() {
        // Given
        CardSearchRequest request = CardSearchRequest.builder()
                .status(Card.CardStatus.WATCH)
                .page(0)
                .size(50)
                .build();
        
        Page<Card> cardPage = new PageImpl<>(List.of(testCard));
        when(cardRepository.findByUserIdAndStatus(eq("user1"), eq(Card.CardStatus.WATCH), any(Pageable.class)))
                .thenReturn(cardPage);
        when(stockDataService.getSnapshot("2330")).thenReturn(testSnapshot);
        
        // When
        PagedResponse<CardDto> result = kanbanService.getCards("user1", request);
        
        // Then
        assertThat(result.getData()).hasSize(1);
        verify(cardRepository).findByUserIdAndStatus(eq("user1"), eq(Card.CardStatus.WATCH), any(Pageable.class));
    }
    
    @Test
    void getCard_ShouldReturnCardDto() {
        // Given
        when(cardRepository.findByIdAndUserId("card1", "user1")).thenReturn(Optional.of(testCard));
        when(stockDataService.getSnapshot("2330")).thenReturn(testSnapshot);
        
        // When
        CardDto result = kanbanService.getCard("user1", "card1");
        
        // Then
        assertThat(result.getId()).isEqualTo("card1");
        assertThat(result.getStockCode()).isEqualTo("2330");
        assertThat(result.getCurrentPrice()).isEqualTo(new BigDecimal("580.00"));
    }
    
    @Test
    void getCard_WhenNotFound_ShouldThrowException() {
        // Given
        when(cardRepository.findByIdAndUserId("card1", "user1")).thenReturn(Optional.empty());
        
        // When & Then
        assertThatThrownBy(() -> kanbanService.getCard("user1", "card1"))
                .isInstanceOf(StockNotFoundException.class)
                .hasMessage("卡片不存在");
    }
    
    @Test
    void updateCard_ShouldUpdateStatusAndLogAudit() {
        // Given
        CardUpdateRequest request = CardUpdateRequest.builder()
                .status(Card.CardStatus.READY_TO_BUY)
                .note("更新備註")
                .reason("手動移動")
                .build();
        
        when(cardRepository.findByIdAndUserId("card1", "user1")).thenReturn(Optional.of(testCard));
        when(cardRepository.save(any(Card.class))).thenReturn(testCard);
        when(stockDataService.getSnapshot("2330")).thenReturn(testSnapshot);
        
        // When
        CardDto result = kanbanService.updateCard("user1", "card1", request);
        
        // Then
        assertThat(result.getStatus()).isEqualTo(Card.CardStatus.READY_TO_BUY);
        verify(auditLogService).logCardStatusChange(
                "user1", 
                "card1", 
                Card.CardStatus.WATCH, 
                Card.CardStatus.READY_TO_BUY, 
                "手動移動"
        );
    }
    
    @Test
    void updateCard_OnlyNote_ShouldNotLogAudit() {
        // Given
        CardUpdateRequest request = CardUpdateRequest.builder()
                .note("只更新備註")
                .build();
        
        when(cardRepository.findByIdAndUserId("card1", "user1")).thenReturn(Optional.of(testCard));
        when(cardRepository.save(any(Card.class))).thenReturn(testCard);
        when(stockDataService.getSnapshot("2330")).thenReturn(testSnapshot);
        
        // When
        CardDto result = kanbanService.updateCard("user1", "card1", request);
        
        // Then
        assertThat(result.getNote()).isEqualTo("只更新備註");
        verify(auditLogService, never()).logCardStatusChange(anyString(), anyString(), any(), any(), anyString());
    }
    
    @Test
    void getCardCount_ShouldReturnCount() {
        // Given
        when(cardRepository.countByUserIdAndStatusNot("user1", Card.CardStatus.ARCHIVED)).thenReturn(5L);
        
        // When
        long result = kanbanService.getCardCount("user1");
        
        // Then
        assertThat(result).isEqualTo(5L);
    }
    
    @Test
    void getCardCountByStatus_ShouldReturnStatusCount() {
        // Given
        when(cardRepository.countByUserIdAndStatus("user1", Card.CardStatus.WATCH)).thenReturn(3L);
        
        // When
        long result = kanbanService.getCardCountByStatus("user1", Card.CardStatus.WATCH);
        
        // Then
        assertThat(result).isEqualTo(3L);
    }
}