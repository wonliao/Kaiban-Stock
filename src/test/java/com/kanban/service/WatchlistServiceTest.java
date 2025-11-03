package com.kanban.service;

import com.kanban.domain.entity.Card;
import com.kanban.domain.entity.StockSnapshot;
import com.kanban.domain.entity.User;
import com.kanban.domain.entity.Watchlist;
import com.kanban.dto.AddStockRequest;
import com.kanban.dto.WatchlistCreateRequest;
import com.kanban.dto.WatchlistDto;
import com.kanban.exception.*;
import com.kanban.repository.CardRepository;
import com.kanban.repository.UserRepository;
import com.kanban.repository.WatchlistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WatchlistServiceTest {
    
    @Mock
    private WatchlistRepository watchlistRepository;
    
    @Mock
    private CardRepository cardRepository;
    
    @Mock
    private UserRepository userRepository;
    
    @Mock
    private StockDataService stockDataService;
    
    @InjectMocks
    private WatchlistService watchlistService;
    
    private User testUser;
    private Watchlist testWatchlist;
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
                .createdAt(LocalDateTime.now())
                .build();
        
        testSnapshot = StockSnapshot.builder()
                .code("2330")
                .name("台積電")
                .currentPrice(new BigDecimal("580.00"))
                .changePercent(new BigDecimal("2.5"))
                .volume(25000000L)
                .build();
    }
    
    @Test
    void getUserWatchlists_ShouldReturnWatchlistDtos() {
        // Given
        when(watchlistRepository.findByUserId("user1")).thenReturn(List.of(testWatchlist));
        when(watchlistRepository.countCardsByWatchlistId("watchlist1")).thenReturn(0L);
        
        // When
        List<WatchlistDto> result = watchlistService.getUserWatchlists("user1");
        
        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("我的觀察清單");
        assertThat(result.get(0).getCurrentSize()).isEqualTo(0);
    }
    
    @Test
    void createWatchlist_ShouldCreateSuccessfully() {
        // Given
        WatchlistCreateRequest request = WatchlistCreateRequest.builder()
                .name("新觀察清單")
                .maxSize(300)
                .build();
        
        when(watchlistRepository.existsByUserIdAndName("user1", "新觀察清單")).thenReturn(false);
        when(userRepository.findById("user1")).thenReturn(Optional.of(testUser));
        when(watchlistRepository.save(any(Watchlist.class))).thenReturn(testWatchlist);
        when(watchlistRepository.countCardsByWatchlistId(any())).thenReturn(0L);
        
        // When
        WatchlistDto result = watchlistService.createWatchlist("user1", request);
        
        // Then
        assertThat(result).isNotNull();
        verify(watchlistRepository).save(any(Watchlist.class));
    }
    
    @Test
    void createWatchlist_ShouldThrowExceptionWhenNameExists() {
        // Given
        WatchlistCreateRequest request = WatchlistCreateRequest.builder()
                .name("已存在的清單")
                .build();
        
        when(watchlistRepository.existsByUserIdAndName("user1", "已存在的清單")).thenReturn(true);
        
        // When & Then
        assertThatThrownBy(() -> watchlistService.createWatchlist("user1", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("觀察清單名稱已存在");
    }
    
    @Test
    void addStockToWatchlist_ShouldAddSuccessfully() {
        // Given
        AddStockRequest request = AddStockRequest.builder()
                .stockCode("2330")
                .note("台積電測試")
                .build();
        
        when(watchlistRepository.findByUserIdAndId("user1", "watchlist1")).thenReturn(Optional.of(testWatchlist));
        when(cardRepository.existsByUserIdAndStockCode("user1", "2330")).thenReturn(false);
        when(watchlistRepository.countCardsByWatchlistId("watchlist1")).thenReturn(0L);
        when(stockDataService.getSnapshot("2330")).thenReturn(testSnapshot);
        when(userRepository.findById("user1")).thenReturn(Optional.of(testUser));
        
        // When
        watchlistService.addStockToWatchlist("user1", "watchlist1", request);
        
        // Then
        verify(cardRepository).save(any(Card.class));
    }
    
    @Test
    void addStockToWatchlist_ShouldThrowExceptionWhenDuplicate() {
        // Given
        AddStockRequest request = AddStockRequest.builder()
                .stockCode("2330")
                .build();
        
        when(watchlistRepository.findByUserIdAndId("user1", "watchlist1")).thenReturn(Optional.of(testWatchlist));
        when(cardRepository.existsByUserIdAndStockCode("user1", "2330")).thenReturn(true);
        
        // When & Then
        assertThatThrownBy(() -> watchlistService.addStockToWatchlist("user1", "watchlist1", request))
                .isInstanceOf(DuplicateStockException.class)
                .hasMessage("股票已存在於觀察清單中");
    }
    
    @Test
    void addStockToWatchlist_ShouldThrowExceptionWhenCapacityExceeded() {
        // Given
        AddStockRequest request = AddStockRequest.builder()
                .stockCode("2330")
                .build();
        
        when(watchlistRepository.findByUserIdAndId("user1", "watchlist1")).thenReturn(Optional.of(testWatchlist));
        when(cardRepository.existsByUserIdAndStockCode("user1", "2330")).thenReturn(false);
        when(watchlistRepository.countCardsByWatchlistId("watchlist1")).thenReturn(500L);
        
        // When & Then
        assertThatThrownBy(() -> watchlistService.addStockToWatchlist("user1", "watchlist1", request))
                .isInstanceOf(WatchlistCapacityExceededException.class)
                .hasMessage("觀察清單已達上限 500 檔股票");
    }
    
    @Test
    void addStockToWatchlist_ShouldThrowExceptionWhenStockNotFound() {
        // Given
        AddStockRequest request = AddStockRequest.builder()
                .stockCode("9999")
                .build();
        
        when(watchlistRepository.findByUserIdAndId("user1", "watchlist1")).thenReturn(Optional.of(testWatchlist));
        when(cardRepository.existsByUserIdAndStockCode("user1", "9999")).thenReturn(false);
        when(watchlistRepository.countCardsByWatchlistId("watchlist1")).thenReturn(0L);
        when(stockDataService.getSnapshot("9999")).thenReturn(null);
        
        // When & Then
        assertThatThrownBy(() -> watchlistService.addStockToWatchlist("user1", "watchlist1", request))
                .isInstanceOf(StockNotFoundException.class)
                .hasMessage("股票代碼不存在於 TWSE 上市清單中");
    }
    
    @Test
    void removeStockFromWatchlist_ShouldArchiveCard() {
        // Given
        Card testCard = Card.builder()
                .id("card1")
                .user(testUser)
                .stockCode("2330")
                .status(Card.CardStatus.WATCH)
                .build();
        
        when(cardRepository.findByUserIdAndStockCode("user1", "2330")).thenReturn(Optional.of(testCard));
        
        // When
        watchlistService.removeStockFromWatchlist("user1", "2330");
        
        // Then
        verify(cardRepository).save(argThat(card -> card.getStatus() == Card.CardStatus.ARCHIVED));
    }
    
    @Test
    void removeStockFromWatchlist_ShouldThrowExceptionWhenNotFound() {
        // Given
        when(cardRepository.findByUserIdAndStockCode("user1", "2330")).thenReturn(Optional.empty());
        
        // When & Then
        assertThatThrownBy(() -> watchlistService.removeStockFromWatchlist("user1", "2330"))
                .isInstanceOf(StockNotFoundException.class)
                .hasMessage("股票不存在於觀察清單中");
    }
}