package com.kanban.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddStockRequest {
    
    @NotBlank(message = "股票代碼不能為空")
    @Pattern(regexp = "^[0-9]{4}$", message = "股票代碼格式不正確，應為4位數字")
    private String stockCode;
    
    private String note;
}