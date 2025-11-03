package com.kanban.dto;

import com.kanban.domain.entity.Card;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
}