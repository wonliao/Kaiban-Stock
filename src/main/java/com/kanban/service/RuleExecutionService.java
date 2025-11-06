package com.kanban.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kanban.domain.entity.*;
import com.kanban.dto.PagedResponse;
import com.kanban.dto.notification.RuleNotificationEvent;
import com.kanban.dto.rule.RuleExecutionDto;
import com.kanban.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 規則執行服務 - 執行規則評估和卡片狀態更新
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RuleExecutionService {

    private final RuleRepository ruleRepository;
    private final RuleExecutionRepository executionRepository;
    private final CardRepository cardRepository;
    private final StockSnapshotRepository stockSnapshotRepository;
    private final TechnicalIndicatorRepository technicalIndicatorRepository;
    private final RuleEvaluationService evaluationService;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    /**
     * 定時執行所有啟用的規則
     * 每5分鐘執行一次
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    @Transactional
    public void executeAllActiveRules() {
        log.info("開始執行定時規則評估");

        LocalDateTime now = LocalDateTime.now();

        // 查詢所有需要執行的規則（排除冷卻期內的規則）
        List<Rule> rules = ruleRepository.findAll().stream()
            .filter(Rule::getEnabled)
            .filter(rule -> isRuleReadyForExecution(rule, now))
            .collect(Collectors.toList());

        log.info("找到 {} 條待執行規則", rules.size());

        for (Rule rule : rules) {
            try {
                executeRuleForAllCards(rule);
            } catch (Exception e) {
                log.error("執行規則失敗: ruleId={}, ruleName={}, error={}",
                         rule.getId(), rule.getName(), e.getMessage(), e);
            }
        }

        log.info("定時規則評估完成");
    }

    /**
     * 執行規則對所有相關卡片
     */
    @Transactional
    public void executeRuleForAllCards(Rule rule) {
        // 取得使用者的所有卡片
        List<Card> cards = cardRepository.findByUser(rule.getUser());

        log.info("執行規則: ruleId={}, ruleName={}, 卡片數量={}",
                rule.getId(), rule.getName(), cards.size());

        int successCount = 0;
        int failedCount = 0;
        int skippedCount = 0;

        for (Card card : cards) {
            try {
                RuleExecution.ExecutionStatus status = executeRuleForCard(rule, card);

                switch (status) {
                    case SUCCESS -> successCount++;
                    case FAILED -> failedCount++;
                    case SKIPPED, COOLDOWN -> skippedCount++;
                }
            } catch (Exception e) {
                log.error("執行規則失敗: ruleId={}, cardId={}, error={}",
                         rule.getId(), card.getId(), e.getMessage(), e);
                failedCount++;
            }
        }

        log.info("規則執行完成: ruleId={}, 成功={}, 失敗={}, 跳過={}",
                rule.getId(), successCount, failedCount, skippedCount);
    }

    /**
     * 執行規則對單一卡片
     */
    @Transactional
    public RuleExecution.ExecutionStatus executeRuleForCard(Rule rule, Card card) {
        long startTime = System.currentTimeMillis();

        // 檢查冷卻時間
        if (!isCardReadyForRuleExecution(rule, card)) {
            log.debug("卡片在冷卻期內: ruleId={}, cardId={}", rule.getId(), card.getId());
            return RuleExecution.ExecutionStatus.COOLDOWN;
        }

        // 取得股票資料
        StockSnapshot stockSnapshot = stockSnapshotRepository
            .findLatestByCode(card.getStockCode())
            .orElse(null);

        if (stockSnapshot == null) {
            log.warn("找不到股票快照: stockCode={}", card.getStockCode());
            recordExecution(rule, card, RuleExecution.ExecutionStatus.SKIPPED,
                          null, null, "找不到股票資料", startTime);
            return RuleExecution.ExecutionStatus.SKIPPED;
        }

        // 取得技術指標
        TechnicalIndicator technicalIndicator = technicalIndicatorRepository
            .findLatestByStockCode(card.getStockCode())
            .orElse(null);

        // 評估規則條件
        RuleEvaluationService.EvaluationResult evaluationResult =
            evaluationService.evaluate(
                rule.getConditionExpression(),
                card,
                stockSnapshot,
                technicalIndicator
            );

        if (!evaluationResult.isSuccess()) {
            log.error("規則評估失敗: ruleId={}, cardId={}, error={}",
                     rule.getId(), card.getId(), evaluationResult.getErrorMessage());
            recordExecution(rule, card, RuleExecution.ExecutionStatus.FAILED,
                          evaluationResult, stockSnapshot,
                          "評估失敗: " + evaluationResult.getErrorMessage(), startTime);
            return RuleExecution.ExecutionStatus.FAILED;
        }

        // 如果條件不匹配，跳過
        if (!evaluationResult.isMatched()) {
            log.debug("規則條件不匹配: ruleId={}, cardId={}", rule.getId(), card.getId());
            recordExecution(rule, card, RuleExecution.ExecutionStatus.SKIPPED,
                          evaluationResult, stockSnapshot, "條件不匹配", startTime);
            return RuleExecution.ExecutionStatus.SKIPPED;
        }

        // 更新卡片狀態
        Card.CardStatus previousStatus = card.getStatus();
        Card.CardStatus newStatus = rule.getTargetStatus();

        if (previousStatus == newStatus) {
            log.debug("卡片狀態已經是目標狀態: cardId={}, status={}",
                     card.getId(), newStatus);
            recordExecution(rule, card, RuleExecution.ExecutionStatus.SKIPPED,
                          evaluationResult, stockSnapshot, "狀態已是目標狀態", startTime);
            return RuleExecution.ExecutionStatus.SKIPPED;
        }

        // 執行狀態更新
        card.setStatus(newStatus);
        cardRepository.save(card);

        // 更新規則執行資訊
        rule.setLastExecutedAt(LocalDateTime.now());
        rule.setTriggerCount(rule.getTriggerCount() + 1);
        ruleRepository.save(rule);

        // 記錄稽核日誌
        auditLogService.logCardStatusChange(
            card.getUser().getId(),
            card.getId(),
            previousStatus,
            newStatus,
            String.format("規則 '%s' 觸發", rule.getName())
        );

        // 記錄執行成功
        String message = String.format("規則觸發成功: %s -> %s",
                                      previousStatus.getDisplayName(),
                                      newStatus.getDisplayName());
        recordExecution(rule, card, RuleExecution.ExecutionStatus.SUCCESS,
                      evaluationResult, stockSnapshot, message, startTime,
                      previousStatus, newStatus);

        log.info("規則執行成功: ruleId={}, cardId={}, {} -> {}",
                rule.getId(), card.getId(), previousStatus, newStatus);

        // 發送通知
        if (rule.getSendNotification()) {
            sendRuleTriggeredNotification(rule, card, previousStatus, newStatus);
        }

        return RuleExecution.ExecutionStatus.SUCCESS;
    }

    /**
     * 取得規則執行歷史（分頁）
     */
    @Transactional(readOnly = true)
    public PagedResponse<RuleExecutionDto> getRuleExecutions(String ruleId, Pageable pageable) {
        Rule rule = ruleRepository.findById(ruleId)
            .orElseThrow(() -> new RuntimeException("規則不存在"));

        Page<RuleExecution> executions = executionRepository.findByRule(rule, pageable);

        List<RuleExecutionDto> dtos = executions.getContent().stream()
            .map(this::convertToDto)
            .collect(Collectors.toList());

        return PagedResponse.<RuleExecutionDto>builder()
            .success(true)
            .data(dtos)
            .pagination(PagedResponse.PaginationInfo.builder()
                .page(executions.getNumber())
                .size(executions.getSize())
                .totalElements(executions.getTotalElements())
                .totalPages(executions.getTotalPages())
                .hasNext(!executions.isLast())
                .hasPrevious(!executions.isFirst())
                .build())
            .build();
    }

    /**
     * 取得卡片的規則執行歷史（分頁）
     */
    @Transactional(readOnly = true)
    public PagedResponse<RuleExecutionDto> getCardExecutions(String cardId, Pageable pageable) {
        Card card = cardRepository.findById(cardId)
            .orElseThrow(() -> new RuntimeException("卡片不存在"));

        Page<RuleExecution> executions = executionRepository.findByCard(card, pageable);

        List<RuleExecutionDto> dtos = executions.getContent().stream()
            .map(this::convertToDto)
            .collect(Collectors.toList());

        return PagedResponse.<RuleExecutionDto>builder()
            .success(true)
            .data(dtos)
            .pagination(PagedResponse.PaginationInfo.builder()
                .page(executions.getNumber())
                .size(executions.getSize())
                .totalElements(executions.getTotalElements())
                .totalPages(executions.getTotalPages())
                .hasNext(!executions.isLast())
                .hasPrevious(!executions.isFirst())
                .build())
            .build();
    }

    // ==================== Private Methods ====================

    /**
     * 檢查規則是否準備好執行（檢查冷卻時間）
     */
    private boolean isRuleReadyForExecution(Rule rule, LocalDateTime now) {
        if (rule.getLastExecutedAt() == null) {
            return true;
        }

        LocalDateTime cooldownThreshold = now.minusSeconds(rule.getCooldownSeconds());
        return rule.getLastExecutedAt().isBefore(cooldownThreshold);
    }

    /**
     * 檢查卡片是否準備好執行規則（檢查該規則對該卡片的冷卻時間）
     */
    private boolean isCardReadyForRuleExecution(Rule rule, Card card) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cooldownThreshold = now.minusSeconds(rule.getCooldownSeconds());

        return executionRepository.findFirstByRuleAndCardOrderByExecutedAtDesc(rule, card)
            .map(lastExecution -> lastExecution.getExecutedAt().isBefore(cooldownThreshold))
            .orElse(true);
    }

    /**
     * 記錄執行結果
     */
    private void recordExecution(
            Rule rule,
            Card card,
            RuleExecution.ExecutionStatus status,
            RuleEvaluationService.EvaluationResult evaluationResult,
            StockSnapshot stockSnapshot,
            String message,
            long startTime) {
        recordExecution(rule, card, status, evaluationResult, stockSnapshot,
                      message, startTime, null, null);
    }

    /**
     * 記錄執行結果（完整版本）
     */
    private void recordExecution(
            Rule rule,
            Card card,
            RuleExecution.ExecutionStatus status,
            RuleEvaluationService.EvaluationResult evaluationResult,
            StockSnapshot stockSnapshot,
            String message,
            long startTime,
            Card.CardStatus previousStatus,
            Card.CardStatus newStatus) {

        long executionTime = System.currentTimeMillis() - startTime;

        String conditionResult = evaluationResult != null
            ? evaluationResult.toJson()
            : null;

        String stockSnapshotJson = null;
        if (stockSnapshot != null) {
            try {
                Map<String, Object> snapshotData = new HashMap<>();
                snapshotData.put("stockCode", stockSnapshot.getCode());
                snapshotData.put("stockName", stockSnapshot.getName());
                snapshotData.put("currentPrice", stockSnapshot.getCurrentPrice());
                snapshotData.put("volume", stockSnapshot.getVolume());
                snapshotData.put("changePercent", stockSnapshot.getChangePercent());
                snapshotData.put("openPrice", stockSnapshot.getOpenPrice());
                snapshotData.put("highPrice", stockSnapshot.getHighPrice());
                snapshotData.put("lowPrice", stockSnapshot.getLowPrice());
                snapshotData.put("updatedAt", stockSnapshot.getUpdatedAt());
                stockSnapshotJson = objectMapper.writeValueAsString(snapshotData);
            } catch (Exception e) {
                log.warn("無法序列化股票快照: {}", e.getMessage());
            }
        }

        RuleExecution execution = RuleExecution.builder()
            .rule(rule)
            .card(card)
            .status(status)
            .previousStatus(previousStatus)
            .newStatus(newStatus)
            .conditionResult(conditionResult)
            .stockSnapshot(stockSnapshotJson)
            .message(message)
            .notificationSent(false)
            .executionTimeMs(executionTime)
            .build();

        executionRepository.save(execution);
    }

    /**
     * 發送規則觸發通知
     */
    private void sendRuleTriggeredNotification(
            Rule rule,
            Card card,
            Card.CardStatus previousStatus,
            Card.CardStatus newStatus) {

        RuleNotificationEvent event = RuleNotificationEvent.builder()
            .userId(card.getUser().getId())
            .ruleId(rule.getId())
            .ruleName(rule.getName())
            .cardId(card.getId())
            .stockCode(card.getStockCode())
            .stockName(card.getStockName())
            .previousStatus(previousStatus)
            .newStatus(newStatus)
            .message(rule.getNotificationTemplate() != null
                ? rule.getNotificationTemplate()
                : String.format("規則 '%s' 已觸發", rule.getName()))
            .triggeredAt(LocalDateTime.now())
            .build();

        notificationService.createRuleTriggeredNotification(event);
    }

    /**
     * 轉換為 DTO
     */
    private RuleExecutionDto convertToDto(RuleExecution execution) {
        return RuleExecutionDto.builder()
            .id(execution.getId())
            .ruleId(execution.getRule().getId())
            .ruleName(execution.getRule().getName())
            .cardId(execution.getCard().getId())
            .stockCode(execution.getCard().getStockCode())
            .stockName(execution.getCard().getStockName())
            .status(execution.getStatus())
            .previousStatus(execution.getPreviousStatus())
            .newStatus(execution.getNewStatus())
            .conditionResult(execution.getConditionResult())
            .stockSnapshot(execution.getStockSnapshot())
            .message(execution.getMessage())
            .notificationSent(execution.getNotificationSent())
            .executionTimeMs(execution.getExecutionTimeMs())
            .executedAt(execution.getExecutedAt())
            .build();
    }
}
