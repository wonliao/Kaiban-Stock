package com.kanban.dto.twse;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

/**
 * TWSE-MCP API 統一回應格式
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TwseApiResponse<T> {
    
    @JsonProperty("stat")
    private String status;
    
    @JsonProperty("date")
    private String date;
    
    @JsonProperty("title")
    private String title;
    
    @JsonProperty("fields")
    private List<String> fields;
    
    @JsonProperty("data")
    private List<T> data;
    
    @JsonProperty("notes")
    private List<String> notes;
    
    public boolean isSuccess() {
        return "OK".equals(status);
    }
}