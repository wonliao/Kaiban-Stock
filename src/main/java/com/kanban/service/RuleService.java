package com.kanban.service;

import com.kanban.domain.entity.Card;
import com.kanban.domain.entity.Rule;
import com.kanban.domain.entity.User;
import com.kanban.dto.PagedResponse;
import com.kanban.dto.rule.RuleCreateRequest;
import com.kanban.dto.rule.RuleDto;
import com.kanban.dto.rule.RuleUpdateRequest;
import com.kanban.exception.ResourceNotFoundException;
import com.kanban.repository.RuleRepository;
import com.kanban.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RuleService {

    private final RuleRepository ruleRepository;
    private final UserRepository userRepository;

    /**
     * 建立新規則
     */
    @Transactional
    public RuleDto createRule(String userId, RuleCreateRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("使用者不存在"));

        // 驗證規則名稱是否重複
        ruleRepository.findByUserAndName(user, request.getName())
            .ifPresent(existingRule -> {
                throw new IllegalArgumentException("規則名稱已存在");
            });

        // 驗證 SpEL 表達式（基本驗證）
        validateSpelExpression(request.getConditionExpression());

        Rule rule = Rule.builder()
            .user(user)
            .name(request.getName())
            .description(request.getDescription())
            .ruleType(request.getRuleType())
            .conditionExpression(request.getConditionExpression())
            .triggerEvent(request.getTriggerEvent())
            .targetStatus(request.getTargetStatus())
            .enabled(request.getEnabled())
            .cooldownSeconds(request.getCooldownSeconds())
            .priority(request.getPriority())
            .sendNotification(request.getSendNotification())
            .notificationTemplate(request.getNotificationTemplate())
            .tags(request.getTags())
            .parameters(request.getParameters())
            .triggerCount(0L)
            .build();

        Rule savedRule = ruleRepository.save(rule);
        log.info("建立規則: userId={}, ruleId={}, name={}", userId, savedRule.getId(), savedRule.getName());

        return convertToDto(savedRule);
    }

    /**
     * 更新規則
     */
    @Transactional
    public RuleDto updateRule(String userId, String ruleId, RuleUpdateRequest request) {
        Rule rule = getRuleByIdAndValidateOwner(ruleId, userId);

        // 如果更新名稱，檢查是否重複
        if (request.getName() != null && !request.getName().equals(rule.getName())) {
            ruleRepository.findByUserAndName(rule.getUser(), request.getName())
                .ifPresent(existingRule -> {
                    throw new IllegalArgumentException("規則名稱已存在");
                });
            rule.setName(request.getName());
        }

        if (request.getDescription() != null) {
            rule.setDescription(request.getDescription());
        }

        if (request.getConditionExpression() != null) {
            validateSpelExpression(request.getConditionExpression());
            rule.setConditionExpression(request.getConditionExpression());
        }

        if (request.getTriggerEvent() != null) {
            rule.setTriggerEvent(request.getTriggerEvent());
        }

        if (request.getTargetStatus() != null) {
            rule.setTargetStatus(request.getTargetStatus());
        }

        if (request.getEnabled() != null) {
            rule.setEnabled(request.getEnabled());
        }

        if (request.getCooldownSeconds() != null) {
            rule.setCooldownSeconds(request.getCooldownSeconds());
        }

        if (request.getPriority() != null) {
            rule.setPriority(request.getPriority());
        }

        if (request.getSendNotification() != null) {
            rule.setSendNotification(request.getSendNotification());
        }

        if (request.getNotificationTemplate() != null) {
            rule.setNotificationTemplate(request.getNotificationTemplate());
        }

        if (request.getTags() != null) {
            rule.setTags(request.getTags());
        }

        if (request.getParameters() != null) {
            rule.setParameters(request.getParameters());
        }

        Rule updatedRule = ruleRepository.save(rule);
        log.info("更新規則: ruleId={}, userId={}", ruleId, userId);

        return convertToDto(updatedRule);
    }

    /**
     * 刪除規則
     */
    @Transactional
    public void deleteRule(String userId, String ruleId) {
        Rule rule = getRuleByIdAndValidateOwner(ruleId, userId);
        ruleRepository.delete(rule);
        log.info("刪除規則: ruleId={}, userId={}", ruleId, userId);
    }

    /**
     * 取得規則詳情
     */
    @Transactional(readOnly = true)
    public RuleDto getRule(String userId, String ruleId) {
        Rule rule = getRuleByIdAndValidateOwner(ruleId, userId);
        return convertToDto(rule);
    }

    /**
     * 取得使用者的所有規則（分頁）
     */
    @Transactional(readOnly = true)
    public PagedResponse<RuleDto> getUserRules(String userId, Pageable pageable) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("使用者不存在"));

        Page<Rule> rules = ruleRepository.findByUser(user, pageable);

        List<RuleDto> ruleDtos = rules.getContent().stream()
            .map(this::convertToDto)
            .collect(Collectors.toList());

        return PagedResponse.<RuleDto>builder()
            .success(true)
            .data(ruleDtos)
            .pagination(PagedResponse.PaginationInfo.builder()
                .page(rules.getNumber())
                .size(rules.getSize())
                .totalElements(rules.getTotalElements())
                .totalPages(rules.getTotalPages())
                .hasNext(!rules.isLast())
                .hasPrevious(!rules.isFirst())
                .build())
            .build();
    }

    /**
     * 取得使用者的啟用規則
     */
    @Transactional(readOnly = true)
    public List<RuleDto> getActiveRules(String userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("使用者不存在"));

        return ruleRepository.findByUserAndEnabledTrue(user).stream()
            .map(this::convertToDto)
            .collect(Collectors.toList());
    }

    /**
     * 啟用/停用規則
     */
    @Transactional
    public RuleDto toggleRuleStatus(String userId, String ruleId, boolean enabled) {
        Rule rule = getRuleByIdAndValidateOwner(ruleId, userId);
        rule.setEnabled(enabled);
        Rule updatedRule = ruleRepository.save(rule);
        log.info("{}規則: ruleId={}, userId={}", enabled ? "啟用" : "停用", ruleId, userId);
        return convertToDto(updatedRule);
    }

    /**
     * 取得預設規則模板
     */
    public List<RuleDto> getDefaultRuleTemplates() {
        // 返回系統預設的規則模板
        return List.of(
            createPriceAlertTemplate(),
            createVolumeSpikeTemplate(),
            createRsiOversoldTemplate(),
            createRsiOverboughtTemplate(),
            createMaCrossoverTemplate()
        );
    }

    /**
     * 從模板建立規則
     */
    @Transactional
    public RuleDto createRuleFromTemplate(String userId, String templateName) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("使用者不存在"));

        RuleDto template = getTemplateByName(templateName);
        if (template == null) {
            throw new IllegalArgumentException("找不到規則模板");
        }

        Rule rule = Rule.builder()
            .user(user)
            .name(template.getName() + " " + System.currentTimeMillis())
            .description(template.getDescription())
            .ruleType(Rule.RuleType.PREDEFINED)
            .conditionExpression(template.getConditionExpression())
            .triggerEvent(template.getTriggerEvent())
            .targetStatus(template.getTargetStatus())
            .enabled(true)
            .cooldownSeconds(template.getCooldownSeconds())
            .priority(template.getPriority())
            .sendNotification(template.getSendNotification())
            .notificationTemplate(template.getNotificationTemplate())
            .triggerCount(0L)
            .build();

        Rule savedRule = ruleRepository.save(rule);
        log.info("從模板建立規則: userId={}, templateName={}, ruleId={}",
                 userId, templateName, savedRule.getId());

        return convertToDto(savedRule);
    }

    // ==================== Private Methods ====================

    /**
     * 取得規則並驗證擁有者
     */
    private Rule getRuleByIdAndValidateOwner(String ruleId, String userId) {
        Rule rule = ruleRepository.findById(ruleId)
            .orElseThrow(() -> new ResourceNotFoundException("規則不存在"));

        if (!rule.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("無權限存取此規則");
        }

        return rule;
    }

    /**
     * 驗證 SpEL 表達式（基本驗證）
     */
    private void validateSpelExpression(String expression) {
        if (expression == null || expression.trim().isEmpty()) {
            throw new IllegalArgumentException("條件表達式不能為空");
        }
        // TODO: 實作完整的 SpEL 表達式驗證
        // 可以使用 SpelExpressionParser 進行解析驗證
    }

    /**
     * 轉換為 DTO
     */
    private RuleDto convertToDto(Rule rule) {
        return RuleDto.builder()
            .id(rule.getId())
            .name(rule.getName())
            .description(rule.getDescription())
            .ruleType(rule.getRuleType())
            .conditionExpression(rule.getConditionExpression())
            .triggerEvent(rule.getTriggerEvent())
            .targetStatus(rule.getTargetStatus())
            .enabled(rule.getEnabled())
            .cooldownSeconds(rule.getCooldownSeconds())
            .priority(rule.getPriority())
            .sendNotification(rule.getSendNotification())
            .notificationTemplate(rule.getNotificationTemplate())
            .tags(rule.getTags())
            .parameters(rule.getParameters())
            .lastExecutedAt(rule.getLastExecutedAt())
            .triggerCount(rule.getTriggerCount())
            .createdAt(rule.getCreatedAt())
            .updatedAt(rule.getUpdatedAt())
            .build();
    }

    // ==================== Rule Templates ====================

    private RuleDto createPriceAlertTemplate() {
        return RuleDto.builder()
            .name("價格警示")
            .description("當股價超過設定的門檻時觸發")
            .ruleType(Rule.RuleType.TEMPLATE)
            .conditionExpression("price >= targetPrice")
            .triggerEvent(Rule.TriggerEvent.PRICE_CHANGE)
            .targetStatus(Card.CardStatus.ALERTS)
            .enabled(true)
            .cooldownSeconds(3600)
            .priority(5)
            .sendNotification(true)
            .notificationTemplate("股票 {stockName} ({stockCode}) 價格已達 {price}，已超過目標價格 {targetPrice}")
            .build();
    }

    private RuleDto createVolumeSpikeTemplate() {
        return RuleDto.builder()
            .name("成交量異常")
            .description("當成交量超過平均成交量的2倍時觸發")
            .ruleType(Rule.RuleType.TEMPLATE)
            .conditionExpression("volume > avgVolume * 2")
            .triggerEvent(Rule.TriggerEvent.VOLUME_SPIKE)
            .targetStatus(Card.CardStatus.ALERTS)
            .enabled(true)
            .cooldownSeconds(7200)
            .priority(3)
            .sendNotification(true)
            .notificationTemplate("股票 {stockName} ({stockCode}) 成交量異常，當前成交量 {volume} 超過平均的2倍")
            .build();
    }

    private RuleDto createRsiOversoldTemplate() {
        return RuleDto.builder()
            .name("RSI超賣訊號")
            .description("當 RSI 低於30時觸發（可能是買進訊號）")
            .ruleType(Rule.RuleType.TEMPLATE)
            .conditionExpression("rsi < 30")
            .triggerEvent(Rule.TriggerEvent.TECHNICAL_INDICATOR)
            .targetStatus(Card.CardStatus.READY_TO_BUY)
            .enabled(true)
            .cooldownSeconds(14400)
            .priority(4)
            .sendNotification(true)
            .notificationTemplate("股票 {stockName} ({stockCode}) RSI 為 {rsi}，已進入超賣區")
            .build();
    }

    private RuleDto createRsiOverboughtTemplate() {
        return RuleDto.builder()
            .name("RSI超買訊號")
            .description("當 RSI 高於70時觸發（可能是賣出訊號）")
            .ruleType(Rule.RuleType.TEMPLATE)
            .conditionExpression("rsi > 70")
            .triggerEvent(Rule.TriggerEvent.TECHNICAL_INDICATOR)
            .targetStatus(Card.CardStatus.SELL)
            .enabled(true)
            .cooldownSeconds(14400)
            .priority(4)
            .sendNotification(true)
            .notificationTemplate("股票 {stockName} ({stockCode}) RSI 為 {rsi}，已進入超買區")
            .build();
    }

    private RuleDto createMaCrossoverTemplate() {
        return RuleDto.builder()
            .name("均線黃金交叉")
            .description("當短期均線上穿長期均線時觸發（黃金交叉）")
            .ruleType(Rule.RuleType.TEMPLATE)
            .conditionExpression("ma5 > ma20 && ma5Previous <= ma20Previous")
            .triggerEvent(Rule.TriggerEvent.TECHNICAL_INDICATOR)
            .targetStatus(Card.CardStatus.READY_TO_BUY)
            .enabled(true)
            .cooldownSeconds(86400)
            .priority(2)
            .sendNotification(true)
            .notificationTemplate("股票 {stockName} ({stockCode}) 出現黃金交叉，MA5={ma5}, MA20={ma20}")
            .build();
    }

    private RuleDto getTemplateByName(String templateName) {
        return getDefaultRuleTemplates().stream()
            .filter(template -> template.getName().equals(templateName))
            .findFirst()
            .orElse(null);
    }
}
