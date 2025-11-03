package com.kanban.controller;

import com.kanban.dto.*;
import com.kanban.service.WatchlistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/watchlist")
@RequiredArgsConstructor
@Slf4j
public class WatchlistController {
    
    private final WatchlistService watchlistService;
    
    @GetMapping
    public ResponseEntity<List<WatchlistDto>> getUserWatchlists(Authentication authentication) {
        String userId = authentication.getName();
        log.debug("GET /api/watchlist - user: {}", userId);
        
        List<WatchlistDto> watchlists = watchlistService.getUserWatchlists(userId);
        return ResponseEntity.ok(watchlists);
    }
    
    @GetMapping("/{watchlistId}")
    public ResponseEntity<WatchlistDto> getWatchlist(
            @PathVariable String watchlistId,
            Authentication authentication) {
        String userId = authentication.getName();
        log.debug("GET /api/watchlist/{} - user: {}", watchlistId, userId);
        
        WatchlistDto watchlist = watchlistService.getWatchlist(userId, watchlistId);
        return ResponseEntity.ok(watchlist);
    }
    
    @PostMapping
    public ResponseEntity<WatchlistDto> createWatchlist(
            @Valid @RequestBody WatchlistCreateRequest request,
            Authentication authentication) {
        String userId = authentication.getName();
        log.debug("POST /api/watchlist - user: {}, name: {}", userId, request.getName());
        
        WatchlistDto watchlist = watchlistService.createWatchlist(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(watchlist);
    }
    
    @DeleteMapping("/{watchlistId}")
    public ResponseEntity<SuccessResponse> deleteWatchlist(
            @PathVariable String watchlistId,
            Authentication authentication) {
        String userId = authentication.getName();
        log.debug("DELETE /api/watchlist/{} - user: {}", watchlistId, userId);
        
        watchlistService.deleteWatchlist(userId, watchlistId);
        return ResponseEntity.ok(SuccessResponse.of("觀察清單已刪除"));
    }
    
    @PostMapping("/{watchlistId}/stocks")
    public ResponseEntity<SuccessResponse> addStock(
            @PathVariable String watchlistId,
            @Valid @RequestBody AddStockRequest request,
            Authentication authentication) {
        String userId = authentication.getName();
        log.debug("POST /api/watchlist/{}/stocks - user: {}, stock: {}", 
                watchlistId, userId, request.getStockCode());
        
        watchlistService.addStockToWatchlist(userId, watchlistId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SuccessResponse.of("股票已新增至觀察清單"));
    }
    
    @DeleteMapping("/stocks/{stockCode}")
    public ResponseEntity<SuccessResponse> removeStock(
            @PathVariable String stockCode,
            Authentication authentication) {
        String userId = authentication.getName();
        log.debug("DELETE /api/watchlist/stocks/{} - user: {}", stockCode, userId);
        
        watchlistService.removeStockFromWatchlist(userId, stockCode);
        return ResponseEntity.ok(SuccessResponse.of("股票已從觀察清單移除"));
    }
}