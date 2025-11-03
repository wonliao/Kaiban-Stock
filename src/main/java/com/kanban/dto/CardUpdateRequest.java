package com.kanban.dto;

import com.kanban.domain.entity.Card;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardUpdateRequest {
    
    private Card.CardStatus status;
    
    @Size(max = 1000, message = "備註不能超過1000個字元")
    private String note;
    
    private String reason; // For audit trail
}