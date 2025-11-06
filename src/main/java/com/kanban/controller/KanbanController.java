package com.kanban.controller;

import com.kanban.domain.entity.Card;
import com.kanban.dto.*;
import com.kanban.security.UserPrincipal;
import com.kanban.service.KanbanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/kanban")
@RequiredArgsConstructor
@Slf4j
public class KanbanController {
    
    private final KanbanService kanbanService;
    
    @GetMapping("/cards")
    public ResponseEntity<PagedResponse<CardDto>> getCards(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Card.CardStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "updatedAt") String sort,
            @RequestParam(defaultValue = "DESC") String direction,
            Authentication authentication) {

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        String userId = userPrincipal.getId();
        log.debug("GET /api/kanban/cards - user: {}, query: {}, status: {}, page: {}, size: {}",
                userId, q, status, page, size);
        
        // Validate page size
        if (size > 100) {
            size = 100; // Maximum page size
        }
        
        CardSearchRequest request = CardSearchRequest.builder()
                .query(q)
                .status(status)
                .page(page)
                .size(size)
                .sortBy(sort)
                .sortDirection(direction)
                .build();
        
        PagedResponse<CardDto> response = kanbanService.getCards(userId, request);
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/cards/{cardId}")
    public ResponseEntity<CardDto> getCard(
            @PathVariable String cardId,
            Authentication authentication) {

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        String userId = userPrincipal.getId();
        log.debug("GET /api/kanban/cards/{} - user: {}", cardId, userId);
        
        CardDto card = kanbanService.getCard(userId, cardId);
        return ResponseEntity.ok(card);
    }
    
    @PatchMapping("/cards/{cardId}")
    public ResponseEntity<CardDto> updateCard(
            @PathVariable String cardId,
            @Valid @RequestBody CardUpdateRequest request,
            Authentication authentication) {

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        String userId = userPrincipal.getId();
        log.debug("PATCH /api/kanban/cards/{} - user: {}, request: {}", cardId, userId, request);
        
        CardDto updatedCard = kanbanService.updateCard(userId, cardId, request);
        return ResponseEntity.ok(updatedCard);
    }
    
    @GetMapping("/stats")
    public ResponseEntity<KanbanStatsDto> getKanbanStats(Authentication authentication) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        String userId = userPrincipal.getId();
        log.debug("GET /api/kanban/stats - user: {}", userId);
        
        KanbanStatsDto stats = KanbanStatsDto.builder()
                .totalCards(kanbanService.getCardCount(userId))
                .watchCount(kanbanService.getCardCountByStatus(userId, Card.CardStatus.WATCH))
                .readyToBuyCount(kanbanService.getCardCountByStatus(userId, Card.CardStatus.READY_TO_BUY))
                .holdCount(kanbanService.getCardCountByStatus(userId, Card.CardStatus.HOLD))
                .sellCount(kanbanService.getCardCountByStatus(userId, Card.CardStatus.SELL))
                .alertsCount(kanbanService.getCardCountByStatus(userId, Card.CardStatus.ALERTS))
                .build();
        
        return ResponseEntity.ok(stats);
    }
}