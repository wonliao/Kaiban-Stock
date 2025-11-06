package com.kanban.dto.notification;

import com.kanban.domain.entity.Card;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 規則觸發通知事件
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleNotificationEvent {

    private String userId;
    private String ruleId;
    private String ruleName;
    private String cardId;
    private String stockCode;
    private String stockName;
    private Card.CardStatus previousStatus;
    private Card.CardStatus newStatus;
    private String message;
    private LocalDateTime triggeredAt;
}
