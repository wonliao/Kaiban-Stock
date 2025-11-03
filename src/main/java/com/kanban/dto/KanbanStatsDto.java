package com.kanban.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KanbanStatsDto {
    
    private long totalCards;
    private long watchCount;
    private long readyToBuyCount;
    private long holdCount;
    private long sellCount;
    private long alertsCount;
}