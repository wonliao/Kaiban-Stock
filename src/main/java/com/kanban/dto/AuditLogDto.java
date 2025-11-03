package com.kanban.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogDto {
    
    private String id;
    private String userId;
    private String cardId;
    private String action;
    private String fromStatus;
    private String toStatus;
    private String reason;
    private LocalDateTime createdAt;
    private String traceId;
    private String ipAddress;
}