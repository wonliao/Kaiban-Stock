package com.kanban.dto.rule;

import com.kanban.domain.entity.Card;
import com.kanban.domain.entity.RuleExecution;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleExecutionDto {

    private String id;
    private String ruleId;
    private String ruleName;
    private String cardId;
    private String stockCode;
    private String stockName;
    private RuleExecution.ExecutionStatus status;
    private Card.CardStatus previousStatus;
    private Card.CardStatus newStatus;
    private String conditionResult;
    private String stockSnapshot;
    private String message;
    private Boolean notificationSent;
    private Long executionTimeMs;
    private LocalDateTime executedAt;
}
