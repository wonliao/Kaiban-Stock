package com.kanban.dto;

import com.kanban.domain.entity.Card;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardSearchRequest {
    
    private String query; // Search in stock code, name, or note
    private Card.CardStatus status; // Filter by status
    private String sortBy = "updatedAt"; // Sort field
    private String sortDirection = "DESC"; // ASC or DESC
    private Integer page = 0;
    private Integer size = 50;
    
    public Pageable toPageable() {
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
        
        Sort sort = Sort.by(direction, validSortBy);
        return PageRequest.of(page, size, sort);
    }
}