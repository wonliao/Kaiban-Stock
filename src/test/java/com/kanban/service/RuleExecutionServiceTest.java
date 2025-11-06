package com.kanban.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kanban.domain.entity.*;
import com.kanban.dto.PagedResponse;
import com.kanban.dto.rule.RuleExecutionDto;
import com.kanban.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RuleExecutionService 單元測試")
class RuleExecutionServiceTest {

    @Mock
    private RuleRepository ruleRepository;

    @Mock
    private RuleExecutionRepository executionRepository;

    @Mock
    private CardRepository cardRepository;

    @Mock
    private StockSnapshotRepository stockSnapshotRepository;

    @Mock
    private TechnicalIndicatorRepository technicalIndicatorRepository;

    @Mock
    private RuleEvaluationService evaluationService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private RuleExecutionService executionService;

    private User testUser;
    private Rule testRule;
    private Card testCard;
    private StockSnapshot testSnapshot;
    private TechnicalIndicator testIndicator;

    @BeforeEach
    void setUp() throws Exception {
        testUser = User.builder()
            .id("user-1")
            .username("testuser")
            .email("test@example.com")
            .build();

        testRule = Rule.builder()
            .id("rule-1")
            .user(testUser)
            .name("價格警示")
            .description("當價格超過100時觸發")
            .ruleType(Rule.RuleType.CUSTOM)
            .conditionExpression("price > 100")
            .triggerEvent(Rule.TriggerEvent.PRICE_CHANGE)
            .targetStatus(Card.CardStatus.ALERTS)
            .enabled(true)
            .cooldownSeconds(3600)
            .priority(5)
            .sendNotification(true)
            .triggerCount(0L)
            .build();

        testCard = Card.builder()
            .id("card-1")
            .user(testUser)
            .stockCode("2330")
            .stockName("台積電")
            .status(Card.CardStatus.WATCH)
            .build();

        testSnapshot = StockSnapshot.builder()
            .code("2330")
            .name("台積電")
            .currentPrice(new BigDecimal("550"))
            .openPrice(new BigDecimal("545"))
            .highPrice(new BigDecimal("555"))
            .lowPrice(new BigDecimal("540"))
            .previousClose(new BigDecimal("548"))
            .volume(50000000L)
            .changePercent(new BigDecimal("0.36"))
            .updatedAt(LocalDateTime.now())
            .build();

        testIndicator = TechnicalIndicator.builder()
            .stockCode("2330")
            .ma5(new BigDecimal("545"))
            .ma20(new BigDecimal("530"))
            .rsi14(new BigDecimal("65"))
            .calculationDate(LocalDateTime.now())
            .build();

        lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{\"snapshot\":true}");
    }

    @Test
    @DisplayName("執行規則對單一卡片 - 成功")
    void executeRuleForCard_Success() {
        // Given
        when(stockSnapshotRepository.findLatestByCode("2330"))
            .thenReturn(Optional.of(testSnapshot));
        when(technicalIndicatorRepository.findLatestByStockCode("2330"))
            .thenReturn(Optional.of(testIndicator));

        RuleEvaluationService.EvaluationResult evalResult =
            RuleEvaluationService.EvaluationResult.builder()
                .success(true)
                .matched(true)
                .build();

        when(evaluationService.evaluate(any(), any(), any(), any()))
            .thenReturn(evalResult);

        when(executionRepository.findFirstByRuleAndCardOrderByExecutedAtDesc(testRule, testCard))
            .thenReturn(Optional.empty());

        when(executionRepository.save(any(RuleExecution.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        when(ruleRepository.save(any(Rule.class)))
            .thenReturn(testRule);

        when(cardRepository.save(any(Card.class)))
            .thenReturn(testCard);

        // When
        RuleExecution.ExecutionStatus status = executionService.executeRuleForCard(testRule, testCard);

        // Then
        assertThat(status).isEqualTo(RuleExecution.ExecutionStatus.SUCCESS);
        verify(cardRepository).save(any(Card.class));
        verify(ruleRepository).save(any(Rule.class));

        ArgumentCaptor<RuleExecution> executionCaptor = ArgumentCaptor.forClass(RuleExecution.class);
        verify(executionRepository, times(1)).save(executionCaptor.capture());
        RuleExecution savedExecution = executionCaptor.getValue();
        assertThat(savedExecution.getStatus()).isEqualTo(RuleExecution.ExecutionStatus.SUCCESS);
        assertThat(savedExecution.getPreviousStatus()).isEqualTo(Card.CardStatus.WATCH);
        assertThat(savedExecution.getNewStatus()).isEqualTo(Card.CardStatus.ALERTS);
        assertThat(savedExecution.getConditionResult()).isNotNull();
        assertThat(savedExecution.getStockSnapshot()).isEqualTo("{\"snapshot\":true}");
        assertThat(savedExecution.getMessage()).contains("規則觸發成功").contains("警示");
        assertThat(savedExecution.getNotificationSent()).isFalse();

        verify(notificationService).createRuleTriggeredNotification(any());
    }

    @Test
    @DisplayName("執行規則對單一卡片 - 找不到股票資料")
    void executeRuleForCard_NoStockData() {
        // Given
        when(stockSnapshotRepository.findLatestByCode("2330"))
            .thenReturn(Optional.empty());

        // When
        RuleExecution.ExecutionStatus status = executionService.executeRuleForCard(testRule, testCard);

        // Then
        assertThat(status).isEqualTo(RuleExecution.ExecutionStatus.SKIPPED);
        verify(executionRepository).save(any(RuleExecution.class));
        verify(cardRepository, never()).save(any());
    }

    @Test
    @DisplayName("執行規則對單一卡片 - 條件不匹配")
    void executeRuleForCard_ConditionNotMatched() {
        // Given
        when(stockSnapshotRepository.findLatestByCode("2330"))
            .thenReturn(Optional.of(testSnapshot));
        when(technicalIndicatorRepository.findLatestByStockCode("2330"))
            .thenReturn(Optional.of(testIndicator));

        RuleEvaluationService.EvaluationResult evalResult =
            RuleEvaluationService.EvaluationResult.builder()
                .success(true)
                .matched(false)
                .build();

        when(evaluationService.evaluate(any(), any(), any(), any()))
            .thenReturn(evalResult);

        when(executionRepository.findFirstByRuleAndCardOrderByExecutedAtDesc(testRule, testCard))
            .thenReturn(Optional.empty());

        // When
        RuleExecution.ExecutionStatus status = executionService.executeRuleForCard(testRule, testCard);

        // Then
        assertThat(status).isEqualTo(RuleExecution.ExecutionStatus.SKIPPED);
        verify(cardRepository, never()).save(any());
    }

    @Test
    @DisplayName("執行規則對單一卡片 - 評估失敗")
    void executeRuleForCard_EvaluationFailed() {
        // Given
        when(stockSnapshotRepository.findLatestByCode("2330"))
            .thenReturn(Optional.of(testSnapshot));
        when(technicalIndicatorRepository.findLatestByStockCode("2330"))
            .thenReturn(Optional.of(testIndicator));

        RuleEvaluationService.EvaluationResult evalResult =
            RuleEvaluationService.EvaluationResult.builder()
                .success(false)
                .errorMessage("評估失敗")
                .build();

        when(evaluationService.evaluate(any(), any(), any(), any()))
            .thenReturn(evalResult);

        when(executionRepository.findFirstByRuleAndCardOrderByExecutedAtDesc(testRule, testCard))
            .thenReturn(Optional.empty());

        // When
        RuleExecution.ExecutionStatus status = executionService.executeRuleForCard(testRule, testCard);

        // Then
        assertThat(status).isEqualTo(RuleExecution.ExecutionStatus.FAILED);
        verify(executionRepository).save(any(RuleExecution.class));
    }

    @Test
    @DisplayName("執行規則對單一卡片 - 狀態已是目標狀態")
    void executeRuleForCard_AlreadyTargetStatus() {
        // Given
        testCard.setStatus(Card.CardStatus.ALERTS);  // 已經是目標狀態

        when(stockSnapshotRepository.findLatestByCode("2330"))
            .thenReturn(Optional.of(testSnapshot));
        when(technicalIndicatorRepository.findLatestByStockCode("2330"))
            .thenReturn(Optional.of(testIndicator));

        RuleEvaluationService.EvaluationResult evalResult =
            RuleEvaluationService.EvaluationResult.builder()
                .success(true)
                .matched(true)
                .build();

        when(evaluationService.evaluate(any(), any(), any(), any()))
            .thenReturn(evalResult);

        when(executionRepository.findFirstByRuleAndCardOrderByExecutedAtDesc(testRule, testCard))
            .thenReturn(Optional.empty());

        // When
        RuleExecution.ExecutionStatus status = executionService.executeRuleForCard(testRule, testCard);

        // Then
        assertThat(status).isEqualTo(RuleExecution.ExecutionStatus.SKIPPED);
        verify(cardRepository, never()).save(any());
    }

    @Test
    @DisplayName("執行規則對單一卡片 - 冷卻期內")
    void executeRuleForCard_InCooldown() {
        // Given
        RuleExecution lastExecution = RuleExecution.builder()
            .id("exec-1")
            .rule(testRule)
            .card(testCard)
            .status(RuleExecution.ExecutionStatus.SUCCESS)
            .executedAt(LocalDateTime.now().minusMinutes(30))  // 30分鐘前執行過
            .build();

        when(executionRepository.findFirstByRuleAndCardOrderByExecutedAtDesc(testRule, testCard))
            .thenReturn(Optional.of(lastExecution));

        // When
        RuleExecution.ExecutionStatus status = executionService.executeRuleForCard(testRule, testCard);

        // Then
        assertThat(status).isEqualTo(RuleExecution.ExecutionStatus.COOLDOWN);
        verify(stockSnapshotRepository, never()).findLatestByCode(any());
    }

    @Test
    @DisplayName("執行規則對所有卡片 - 成功")
    void executeRuleForAllCards_Success() {
        // Given
        List<Card> cards = Arrays.asList(testCard);
        when(cardRepository.findByUser(testUser)).thenReturn(cards);

        when(stockSnapshotRepository.findLatestByCode("2330"))
            .thenReturn(Optional.of(testSnapshot));
        when(technicalIndicatorRepository.findLatestByStockCode("2330"))
            .thenReturn(Optional.of(testIndicator));

        RuleEvaluationService.EvaluationResult evalResult =
            RuleEvaluationService.EvaluationResult.builder()
                .success(true)
                .matched(true)
                .build();

        when(evaluationService.evaluate(any(), any(), any(), any()))
            .thenReturn(evalResult);

        when(executionRepository.findFirstByRuleAndCardOrderByExecutedAtDesc(testRule, testCard))
            .thenReturn(Optional.empty());

        when(executionRepository.save(any(RuleExecution.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        when(ruleRepository.save(any(Rule.class)))
            .thenReturn(testRule);

        when(cardRepository.save(any(Card.class)))
            .thenReturn(testCard);

        // When
        executionService.executeRuleForAllCards(testRule);

        // Then
        verify(cardRepository).findByUser(testUser);
        verify(cardRepository, atLeastOnce()).save(any(Card.class));
    }

    @Test
    @DisplayName("執行規則 - 不發送通知")
    void executeRuleForCard_NoNotification() {
        // Given
        testRule.setSendNotification(false);

        when(stockSnapshotRepository.findLatestByCode("2330"))
            .thenReturn(Optional.of(testSnapshot));
        when(technicalIndicatorRepository.findLatestByStockCode("2330"))
            .thenReturn(Optional.of(testIndicator));

        RuleEvaluationService.EvaluationResult evalResult =
            RuleEvaluationService.EvaluationResult.builder()
                .success(true)
                .matched(true)
                .build();

        when(evaluationService.evaluate(any(), any(), any(), any()))
            .thenReturn(evalResult);

        when(executionRepository.findFirstByRuleAndCardOrderByExecutedAtDesc(testRule, testCard))
            .thenReturn(Optional.empty());

        when(executionRepository.save(any(RuleExecution.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        when(ruleRepository.save(any(Rule.class)))
            .thenReturn(testRule);

        when(cardRepository.save(any(Card.class)))
            .thenReturn(testCard);

        // When
        RuleExecution.ExecutionStatus status = executionService.executeRuleForCard(testRule, testCard);

        // Then
        assertThat(status).isEqualTo(RuleExecution.ExecutionStatus.SUCCESS);
        verify(notificationService, never()).createRuleTriggeredNotification(any());
    }

    @Test
    @DisplayName("定時執行僅處理啟用且過冷卻的規則")
    void executeAllActiveRules_FiltersReadyRules() {
        // Given
        Rule readyRule = Rule.builder()
            .id("rule-ready")
            .user(testUser)
            .name("Ready Rule")
            .description("過冷卻的規則")
            .conditionExpression("price > 100")
            .triggerEvent(Rule.TriggerEvent.PRICE_CHANGE)
            .targetStatus(Card.CardStatus.ALERTS)
            .enabled(true)
            .cooldownSeconds(300)
            .lastExecutedAt(LocalDateTime.now().minusMinutes(10))
            .build();

        Rule cooldownRule = Rule.builder()
            .id("rule-cooldown")
            .user(testUser)
            .name("Cooldown Rule")
            .conditionExpression("price > 100")
            .triggerEvent(Rule.TriggerEvent.PRICE_CHANGE)
            .targetStatus(Card.CardStatus.ALERTS)
            .enabled(true)
            .cooldownSeconds(600)
            .lastExecutedAt(LocalDateTime.now().minusSeconds(30))
            .build();

        Rule disabledRule = Rule.builder()
            .id("rule-disabled")
            .user(testUser)
            .name("Disabled Rule")
            .conditionExpression("price > 100")
            .triggerEvent(Rule.TriggerEvent.PRICE_CHANGE)
            .targetStatus(Card.CardStatus.ALERTS)
            .enabled(false)
            .cooldownSeconds(600)
            .lastExecutedAt(LocalDateTime.now().minusHours(2))
            .build();

        when(ruleRepository.findAll()).thenReturn(List.of(readyRule, cooldownRule, disabledRule));
        when(cardRepository.findByUser(testUser)).thenReturn(List.of(testCard));
        when(stockSnapshotRepository.findLatestByCode("2330"))
            .thenReturn(Optional.of(testSnapshot));
        when(technicalIndicatorRepository.findLatestByStockCode("2330"))
            .thenReturn(Optional.of(testIndicator));
        when(executionRepository.findFirstByRuleAndCardOrderByExecutedAtDesc(readyRule, testCard))
            .thenReturn(Optional.empty());

        RuleEvaluationService.EvaluationResult evalResult =
            RuleEvaluationService.EvaluationResult.builder()
                .success(true)
                .matched(true)
                .build();

        when(evaluationService.evaluate(any(), any(), any(), any()))
            .thenReturn(evalResult);

        when(executionRepository.save(any(RuleExecution.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        when(ruleRepository.save(any(Rule.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        when(cardRepository.save(any(Card.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        executionService.executeAllActiveRules();

        // Then
        verify(ruleRepository).findAll();
        verify(cardRepository, times(1)).findByUser(testUser);
        verify(stockSnapshotRepository, times(1)).findLatestByCode("2330");
        verify(technicalIndicatorRepository, times(1)).findLatestByStockCode("2330");
        verify(executionRepository, times(1))
            .findFirstByRuleAndCardOrderByExecutedAtDesc(readyRule, testCard);
        verify(executionRepository, never())
            .findFirstByRuleAndCardOrderByExecutedAtDesc(cooldownRule, testCard);
        verify(executionRepository, never())
            .findFirstByRuleAndCardOrderByExecutedAtDesc(disabledRule, testCard);
        verify(notificationService, times(1)).createRuleTriggeredNotification(any());
        verify(executionRepository, times(1)).save(any(RuleExecution.class));
        verify(evaluationService, times(1)).evaluate(any(), any(), any(), any());
    }

    @Test
    @DisplayName("取得規則執行歷史 - 成功")
    void getRuleExecutions_Success() {
        // Given
        RuleExecution execution = RuleExecution.builder()
            .id("exec-1")
            .rule(testRule)
            .card(testCard)
            .status(RuleExecution.ExecutionStatus.SUCCESS)
            .previousStatus(Card.CardStatus.WATCH)
            .newStatus(Card.CardStatus.ALERTS)
            .conditionResult("{\"matched\":true}")
            .stockSnapshot("{\"snapshot\":true}")
            .message("觸發成功")
            .notificationSent(true)
            .executionTimeMs(120L)
            .executedAt(LocalDateTime.now())
            .build();

        Pageable pageable = PageRequest.of(0, 10);
        when(ruleRepository.findById("rule-1")).thenReturn(Optional.of(testRule));
        when(executionRepository.findByRule(eq(testRule), eq(pageable)))
            .thenReturn(new PageImpl<>(List.of(execution), pageable, 1));

        // When
        PagedResponse<RuleExecutionDto> response = executionService.getRuleExecutions("rule-1", pageable);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getData()).hasSize(1);
        RuleExecutionDto dto = response.getData().get(0);
        assertThat(dto.getRuleId()).isEqualTo("rule-1");
        assertThat(dto.getCardId()).isEqualTo("card-1");
        assertThat(dto.getStatus()).isEqualTo(RuleExecution.ExecutionStatus.SUCCESS);
        assertThat(dto.getMessage()).contains("觸發成功");
        assertThat(response.getPagination().getTotalElements()).isEqualTo(1);
        assertThat(response.getPagination().isHasNext()).isFalse();
    }

    @Test
    @DisplayName("取得卡片執行歷史 - 成功")
    void getCardExecutions_Success() {
        // Given
        RuleExecution execution = RuleExecution.builder()
            .id("exec-2")
            .rule(testRule)
            .card(testCard)
            .status(RuleExecution.ExecutionStatus.SKIPPED)
            .message("條件不匹配")
            .executedAt(LocalDateTime.now())
            .build();

        Pageable pageable = PageRequest.of(0, 5);
        when(cardRepository.findById("card-1")).thenReturn(Optional.of(testCard));
        when(executionRepository.findByCard(eq(testCard), eq(pageable)))
            .thenReturn(new PageImpl<>(List.of(execution), pageable, 1));

        // When
        PagedResponse<RuleExecutionDto> response = executionService.getCardExecutions("card-1", pageable);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getData()).hasSize(1);
        RuleExecutionDto dto = response.getData().get(0);
        assertThat(dto.getCardId()).isEqualTo("card-1");
        assertThat(dto.getStatus()).isEqualTo(RuleExecution.ExecutionStatus.SKIPPED);
        assertThat(dto.getMessage()).isEqualTo("條件不匹配");
        assertThat(response.getPagination().getTotalElements()).isEqualTo(1);
    }
}
