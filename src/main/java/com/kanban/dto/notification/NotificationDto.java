package com.kanban.dto.notification;

import com.kanban.domain.entity.Notification;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDto {

    private String id;
    private String title;
    private String message;
    private Notification.NotificationType type;
    private String ruleId;
    private String cardId;
    private String stockCode;
    private String metadata;
    private Boolean isRead;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
}
