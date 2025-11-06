package com.kanban.dto.rule;

import com.kanban.domain.entity.Card;
import com.kanban.domain.entity.Rule;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleCreateRequest {

    @NotBlank(message = "規則名稱不能為空")
    @Size(max = 200, message = "規則名稱不能超過200個字元")
    private String name;

    @Size(max = 1000, message = "描述不能超過1000個字元")
    private String description;

    @NotNull(message = "規則類型不能為空")
    private Rule.RuleType ruleType;

    @NotBlank(message = "條件表達式不能為空")
    private String conditionExpression;

    @NotNull(message = "觸發事件類型不能為空")
    private Rule.TriggerEvent triggerEvent;

    @NotNull(message = "目標狀態不能為空")
    private Card.CardStatus targetStatus;

    @Builder.Default
    private Boolean enabled = true;

    @NotNull(message = "冷卻時間不能為空")
    @Min(value = 60, message = "冷卻時間不能少於60秒")
    private Integer cooldownSeconds;

    @Builder.Default
    @Min(value = 1, message = "優先級必須大於0")
    private Integer priority = 5;

    @Builder.Default
    private Boolean sendNotification = true;

    private String notificationTemplate;

    private String tags;

    private String parameters;
}
