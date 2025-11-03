package com.kanban.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuccessResponse {
    
    private boolean success;
    private String message;
    private Object data;
    private MetaInfo meta;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetaInfo {
        private Instant timestamp;
        private String traceId;
        private String version;
    }
    
    public static SuccessResponse of(String message) {
        return SuccessResponse.builder()
                .success(true)
                .message(message)
                .meta(MetaInfo.builder()
                        .timestamp(Instant.now())
                        .version("1.0.0")
                        .build())
                .build();
    }
    
    public static SuccessResponse of(String message, Object data) {
        return SuccessResponse.builder()
                .success(true)
                .message(message)
                .data(data)
                .meta(MetaInfo.builder()
                        .timestamp(Instant.now())
                        .version("1.0.0")
                        .build())
                .build();
    }
}