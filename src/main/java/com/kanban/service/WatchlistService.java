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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WatchlistService {
    
    private final WatchlistRepository watchlistRepository;
    private final CardRepository cardRepository;
    private final UserRepository userRepository;
    private final StockDataService stockDataService;
    private final AuditLogService auditLogService;
    
    @Transactional(readOnly = true)
    public List<WatchlistDto> getUserWatchlists(String userId) {
        log.debug("Getting watchlists for user: {}", userId);
        
        List<Watchlist> watchlists = watchlistRepository.findByUserId(userId);
        
        return watchlists.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public WatchlistDto getWatchlist(String userId, String watchlistId) {
        log.debug("Getting watchlist {} for user: {}", watchlistId, userId);
        
        Watchlist watchlist = watchlistRepository.findByUserIdAndId(userId, watchlistId)
                .orElseThrow(() -> new WatchlistNotFoundException("觀察清單不存在"));
        
        return convertToDto(watchlist);
    }
    
    @Transactional
    public WatchlistDto createWatchlist(String userId, WatchlistCreateRequest request) {
        log.debug("Creating watchlist for user: {}", userId);
        
        // Check if watchlist name already exists for user
        if (watchlistRepository.existsByUserIdAndName(userId, request.getName())) {
            throw new IllegalArgumentException("觀察清單名稱已存在");
        }
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("使用者不存在"));
        
        Watchlist watchlist = Watchlist.builder()
                .user(user)
                .name(request.getName())
                .maxSize(request.getMaxSize() != null ? request.getMaxSize() : 500)
                .build();
        
        watchlist = watchlistRepository.save(watchlist);
        log.info("Created watchlist {} for user {}", watchlist.getId(), userId);
        
        // Log audit trail
        auditLogService.logWatchlistChange(userId, "CREATE_WATCHLIST", null, "建立觀察清單: " + watchlist.getName());
        
        return convertToDto(watchlist);
    }
    
    @Transactional
    public void deleteWatchlist(String userId, String watchlistId) {
        log.debug("Deleting watchlist {} for user: {}", watchlistId, userId);
        
        Watchlist watchlist = watchlistRepository.findByUserIdAndId(userId, watchlistId)
                .orElseThrow(() -> new WatchlistNotFoundException("觀察清單不存在"));
        
        // Archive all cards in this watchlist instead of deleting
        List<Card> cards = cardRepository.findByWatchlistId(watchlistId);
        cards.forEach(card -> {
            card.setStatus(Card.CardStatus.ARCHIVED);
            cardRepository.save(card);
        });
        
        watchlistRepository.delete(watchlist);
        log.info("Deleted watchlist {} for user {}", watchlistId, userId);
        
        // Log audit trail
        auditLogService.logWatchlistChange(userId, "DELETE_WATCHLIST", null, "刪除觀察清單: " + watchlist.getName());
    }
    
    @Transactional
    public void addStockToWatchlist(String userId, String watchlistId, AddStockRequest request) {
        log.debug("Adding stock {} to watchlist {} for user: {}", request.getStockCode(), watchlistId, userId);
        
        Watchlist watchlist = watchlistRepository.findByUserIdAndId(userId, watchlistId)
                .orElseThrow(() -> new WatchlistNotFoundException("觀察清單不存在"));
        
        // Check if stock already exists in user's watchlist
        if (cardRepository.existsByUserIdAndStockCode(userId, request.getStockCode())) {
            throw new DuplicateStockException("股票已存在於觀察清單中");
        }
        
        // Check watchlist capacity
        long currentSize = watchlistRepository.countCardsByWatchlistId(watchlistId);
        if (currentSize >= watchlist.getMaxSize()) {
            throw new WatchlistCapacityExceededException(
                String.format("觀察清單已達上限 %d 檔股票", watchlist.getMaxSize()));
        }
        
        // Validate stock code exists in TWSE
        try {
            StockSnapshot snapshot = stockDataService.getSnapshot(request.getStockCode());
            if (snapshot == null) {
                throw new StockNotFoundException("股票代碼不存在於 TWSE 上市清單中");
            }
        } catch (Exception e) {
            log.warn("Failed to validate stock code {}: {}", request.getStockCode(), e.getMessage());
            throw new StockNotFoundException("無法驗證股票代碼，請檢查代碼是否正確");
        }
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("使用者不存在"));
        
        // Create new card with WATCH status
        Card card = Card.builder()
                .user(user)
                .watchlist(watchlist)
                .stockCode(request.getStockCode())
                .status(Card.CardStatus.WATCH)
                .note(request.getNote())
                .build();
        
        cardRepository.save(card);
        log.info("Added stock {} to watchlist {} for user {}", request.getStockCode(), watchlistId, userId);
        
        // Log audit trail
        auditLogService.logWatchlistChange(userId, "ADD_STOCK", request.getStockCode(), "新增股票至觀察清單");
    }
    
    @Transactional
    public void removeStockFromWatchlist(String userId, String stockCode) {
        log.debug("Removing stock {} from watchlist for user: {}", stockCode, userId);
        
        Card card = cardRepository.findByUserIdAndStockCode(userId, stockCode)
                .orElseThrow(() -> new StockNotFoundException("股票不存在於觀察清單中"));
        
        // Archive the card instead of deleting to preserve audit trail
        card.setStatus(Card.CardStatus.ARCHIVED);
        cardRepository.save(card);
        
        log.info("Removed stock {} from watchlist for user {}", stockCode, userId);
        
        // Log audit trail
        auditLogService.logWatchlistChange(userId, "REMOVE_STOCK", stockCode, "從觀察清單移除股票");
    }
    
    private WatchlistDto convertToDto(Watchlist watchlist) {
        long currentSize = watchlistRepository.countCardsByWatchlistId(watchlist.getId());
        
        List<String> stockCodes = watchlist.getCards() != null ? 
                watchlist.getCards().stream()
                        .filter(card -> card.getStatus() != Card.CardStatus.ARCHIVED)
                        .map(Card::getStockCode)
                        .collect(Collectors.toList()) : 
                List.of();
        
        return WatchlistDto.builder()
                .id(watchlist.getId())
                .name(watchlist.getName())
                .maxSize(watchlist.getMaxSize())
                .createdAt(watchlist.getCreatedAt())
                .currentSize((int) currentSize)
                .stockCodes(stockCodes)
                .build();
    }
}