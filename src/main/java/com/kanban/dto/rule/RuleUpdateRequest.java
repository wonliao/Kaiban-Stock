package com.kanban.dto.rule;

import com.kanban.domain.entity.Card;
import com.kanban.domain.entity.Rule;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleUpdateRequest {

    @Size(max = 200, message = "規則名稱不能超過200個字元")
    private String name;

    @Size(max = 1000, message = "描述不能超過1000個字元")
    private String description;

    private String conditionExpression;

    private Rule.TriggerEvent triggerEvent;

    private Card.CardStatus targetStatus;

    private Boolean enabled;

    @Min(value = 60, message = "冷卻時間不能少於60秒")
    private Integer cooldownSeconds;

    @Min(value = 1, message = "優先級必須大於0")
    private Integer priority;

    private Boolean sendNotification;

    private String notificationTemplate;

    private String tags;

    private String parameters;
}
