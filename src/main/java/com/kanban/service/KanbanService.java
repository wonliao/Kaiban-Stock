package com.kanban.service;

import com.kanban.domain.entity.Card;
import com.kanban.domain.entity.StockSnapshot;
import com.kanban.dto.CardDto;
import com.kanban.dto.CardSearchRequest;
import com.kanban.dto.CardUpdateRequest;
import com.kanban.dto.PagedResponse;
import com.kanban.exception.StockNotFoundException;
import com.kanban.repository.CardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class KanbanService {
    
    private final CardRepository cardRepository;
    private final StockDataService stockDataService;
    private final AuditLogService auditLogService;
    
    @Transactional(readOnly = true)
    public PagedResponse<CardDto> getCards(String userId, CardSearchRequest request) {
        log.debug("Getting cards for user: {} with request: {}", userId, request);
        
        // Create pageable with sorting
        Sort sort = createSort(request.getSortBy(), request.getSortDirection());
        Pageable pageable = PageRequest.of(request.getPage(), request.getSize(), sort);
        
        Page<Card> cardPage;
        
        // Apply filters based on request
        if (request.getQuery() != null && !request.getQuery().trim().isEmpty() && request.getStatus() != null) {
            // Both query and status filter
            cardPage = cardRepository.findByUserIdAndStatusAndQuery(userId, request.getStatus(), request.getQuery().trim(), pageable);
        } else if (request.getQuery() != null && !request.getQuery().trim().isEmpty()) {
            // Only query filter
            cardPage = cardRepository.findByUserIdAndQuery(userId, request.getQuery().trim(), pageable);
        } else if (request.getStatus() != null) {
            // Only status filter
            cardPage = cardRepository.findByUserIdAndStatus(userId, request.getStatus(), pageable);
        } else {
            // No filters, exclude archived cards
            cardPage = cardRepository.findByUserIdAndStatusNot(userId, Card.CardStatus.ARCHIVED, pageable);
        }
        
        // Convert to DTOs with stock data
        List<CardDto> cardDtos = cardPage.getContent().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
        
        // Create pagination info
        PagedResponse.PaginationInfo paginationInfo = PagedResponse.PaginationInfo.builder()
                .page(cardPage.getNumber())
                .size(cardPage.getSize())
                .totalElements(cardPage.getTotalElements())
                .totalPages(cardPage.getTotalPages())
                .hasNext(cardPage.hasNext())
                .hasPrevious(cardPage.hasPrevious())
                .build();
        
        // Create meta info
        PagedResponse.MetaInfo metaInfo = PagedResponse.MetaInfo.builder()
                .timestamp(Instant.now())
                .traceId(UUID.randomUUID().toString())
                .version("1.0.0")
                .build();
        
        return PagedResponse.<CardDto>builder()
                .success(true)
                .data(cardDtos)
                .pagination(paginationInfo)
                .meta(metaInfo)
                .build();
    }
    
    @Transactional(readOnly = true)
    public CardDto getCard(String userId, String cardId) {
        log.debug("Getting card {} for user: {}", cardId, userId);
        
        Card card = cardRepository.findByIdAndUserId(cardId, userId)
                .orElseThrow(() -> new StockNotFoundException("卡片不存在"));
        
        return convertToDto(card);
    }
    
    @Transactional
    public CardDto updateCard(String userId, String cardId, CardUpdateRequest request) {
        log.debug("Updating card {} for user: {} with request: {}", cardId, userId, request);
        
        Card card = cardRepository.findByIdAndUserId(cardId, userId)
                .orElseThrow(() -> new StockNotFoundException("卡片不存在"));
        
        Card.CardStatus oldStatus = card.getStatus();
        
        // Update card fields
        if (request.getStatus() != null && request.getStatus() != oldStatus) {
            card.setStatus(request.getStatus());
        }
        
        if (request.getNote() != null) {
            card.setNote(request.getNote());
        }
        
        card = cardRepository.save(card);
        
        // Log audit trail if status changed
        if (request.getStatus() != null && request.getStatus() != oldStatus) {
            auditLogService.logCardStatusChange(
                    userId, 
                    cardId, 
                    oldStatus, 
                    request.getStatus(), 
                    request.getReason() != null ? request.getReason() : "手動更新"
            );
        }
        
        log.info("Updated card {} for user {}: {} -> {}", cardId, userId, oldStatus, card.getStatus());
        
        return convertToDto(card);
    }
    
    @Transactional(readOnly = true)
    public long getCardCount(String userId) {
        return cardRepository.countByUserIdAndStatusNot(userId, Card.CardStatus.ARCHIVED);
    }
    
    @Transactional(readOnly = true)
    public long getCardCountByStatus(String userId, Card.CardStatus status) {
        return cardRepository.countByUserIdAndStatus(userId, status);
    }
    
    private CardDto convertToDto(Card card) {
        CardDto.CardDtoBuilder builder = CardDto.builder()
                .id(card.getId())
                .stockCode(card.getStockCode())
                .stockName(card.getStockName())
                .status(card.getStatus())
                .note(card.getNote())
                .createdAt(card.getCreatedAt())
                .updatedAt(card.getUpdatedAt());
        
        // Enrich with stock data if available
        try {
            StockSnapshot snapshot = stockDataService.getSnapshot(card.getStockCode());
            if (snapshot != null) {
                builder.currentPrice(snapshot.getCurrentPrice())
                       .changePercent(snapshot.getChangePercent())
                       .volume(snapshot.getVolume())
                       .ma20(snapshot.getMa20())
                       .rsi(snapshot.getRsi())
                       .dataUpdatedAt(snapshot.getUpdatedAt())
                       .dataSource(snapshot.getDataSource())
                       .delayMinutes(snapshot.getDelayMinutes());
                
                // Update stock name if it's different
                if (snapshot.getName() != null && !snapshot.getName().equals(card.getStockName())) {
                    builder.stockName(snapshot.getName());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get stock data for {}: {}", card.getStockCode(), e.getMessage());
            // Continue without stock data
        }
        
        return builder.build();
    }
    
    private Sort createSort(String sortBy, String sortDirection) {
        Sort.Direction direction = "ASC".equalsIgnoreCase(sortDirection) ? 
                Sort.Direction.ASC : Sort.Direction.DESC;
        
        // Validate sort field
        String validSortBy = switch (sortBy) {
            case "stockCode" -> "stockCode";
            case "stockName" -> "stockName";
            case "status" -> "status";
            case "createdAt" -> "createdAt";
            case "updatedAt" -> "updatedAt";
            default -> "updatedAt";
        };
        
        return Sort.by(direction, validSortBy);
    }
}