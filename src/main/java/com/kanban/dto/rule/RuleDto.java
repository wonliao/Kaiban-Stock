package com.kanban.dto.rule;

import com.kanban.domain.entity.Card;
import com.kanban.domain.entity.Rule;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleDto {

    private String id;
    private String name;
    private String description;
    private Rule.RuleType ruleType;
    private String conditionExpression;
    private Rule.TriggerEvent triggerEvent;
    private Card.CardStatus targetStatus;
    private Boolean enabled;
    private Integer cooldownSeconds;
    private Integer priority;
    private Boolean sendNotification;
    private String notificationTemplate;
    private String tags;
    private String parameters;
    private LocalDateTime lastExecutedAt;
    private Long triggerCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
