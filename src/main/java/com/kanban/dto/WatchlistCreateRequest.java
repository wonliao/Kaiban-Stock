package com.kanban.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WatchlistCreateRequest {
    
    @NotBlank(message = "觀察清單名稱不能為空")
    @Size(max = 100, message = "觀察清單名稱不能超過100個字元")
    private String name;
    
    private Integer maxSize = 500;
}