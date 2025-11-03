package com.kanban.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WatchlistDto {
    
    private String id;
    private String name;
    private Integer maxSize;
    private LocalDateTime createdAt;
    private Integer currentSize;
    private List<String> stockCodes;
}