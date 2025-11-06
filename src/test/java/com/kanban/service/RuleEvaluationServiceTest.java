package com.kanban.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kanban.domain.entity.Card;
import com.kanban.domain.entity.StockSnapshot;
import com.kanban.domain.entity.TechnicalIndicator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RuleEvaluationService 單元測試")
class RuleEvaluationServiceTest {

    private ObjectMapper objectMapper;
    private RuleEvaluationService evaluationService;

    private Card testCard;
    private StockSnapshot testSnapshot;
    private TechnicalIndicator testIndicator;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        evaluationService = new RuleEvaluationService(objectMapper);
        testCard = Card.builder()
            .id("card-1")
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
            .ma10(new BigDecimal("540"))
            .ma20(new BigDecimal("530"))
            .ma60(new BigDecimal("520"))
            .rsi14(new BigDecimal("65"))
            .macdLine(new BigDecimal("2.5"))
            .macdSignal(new BigDecimal("2.0"))
            .macdHistogram(new BigDecimal("0.5"))
            .kdK(new BigDecimal("75"))
            .kdD(new BigDecimal("70"))
            .volumeRatio(new BigDecimal("1.8"))
            .calculationDate(LocalDateTime.now())
            .build();
    }

    @Test
    @DisplayName("評估簡單價格條件 - 成功匹配")
    void evaluate_SimplePriceCondition_Success() {
        // Given
        String expression = "price > 500";

        // When
        RuleEvaluationService.EvaluationResult result =
            evaluationService.evaluate(expression, testCard, testSnapshot, testIndicator);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isMatched()).isTrue();
        assertThat(result.getVariables()).containsKey("price");
        assertThat(result.getVariables().get("price")).isEqualTo(new BigDecimal("550"));
    }

    @Test
    @DisplayName("評估簡單價格條件 - 不匹配")
    void evaluate_SimplePriceCondition_NotMatched() {
        // Given
        String expression = "price > 600";

        // When
        RuleEvaluationService.EvaluationResult result =
            evaluationService.evaluate(expression, testCard, testSnapshot, testIndicator);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isMatched()).isFalse();
    }

    @Test
    @DisplayName("評估複雜條件 - 價格和成交量")
    void evaluate_ComplexCondition_PriceAndVolume() {
        // Given
        String expression = "price > 500 && volume > 40000000";

        // When
        RuleEvaluationService.EvaluationResult result =
            evaluationService.evaluate(expression, testCard, testSnapshot, testIndicator);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isMatched()).isTrue();
    }

    @Test
    @DisplayName("評估 RSI 條件 - 超買")
    void evaluate_RsiCondition_Overbought() {
        // Given
        String expression = "rsi > 70";

        // When
        RuleEvaluationService.EvaluationResult result =
            evaluationService.evaluate(expression, testCard, testSnapshot, testIndicator);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isMatched()).isFalse();  // RSI is 65, not > 70
    }

    @Test
    @DisplayName("評估 RSI 條件 - 超賣")
    void evaluate_RsiCondition_Oversold() {
        // Given
        String expression = "rsi < 30";
        testIndicator.setRsi14(new BigDecimal("25"));

        // When
        RuleEvaluationService.EvaluationResult result =
            evaluationService.evaluate(expression, testCard, testSnapshot, testIndicator);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isMatched()).isTrue();
    }

    @Test
    @DisplayName("評估均線條件 - MA5 > MA20")
    void evaluate_MovingAverageCondition() {
        // Given
        String expression = "ma5 > ma20";

        // When
        RuleEvaluationService.EvaluationResult result =
            evaluationService.evaluate(expression, testCard, testSnapshot, testIndicator);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isMatched()).isTrue();  // MA5 545 > MA20 530
    }

    @Test
    @DisplayName("評估 MACD 條件")
    void evaluate_MacdCondition() {
        // Given
        String expression = "macd > 0 && macdSignal > 0";

        // When
        RuleEvaluationService.EvaluationResult result =
            evaluationService.evaluate(expression, testCard, testSnapshot, testIndicator);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isMatched()).isTrue();
    }

    @Test
    @DisplayName("評估派生指標 - MA5 與 MA20 差值")
    void evaluate_MaDifferenceCondition() {
        // Given
        String expression = "ma5_ma20_diff > 10";

        // When
        RuleEvaluationService.EvaluationResult result =
            evaluationService.evaluate(expression, testCard, testSnapshot, testIndicator);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isMatched()).isTrue();
    }

    @Test
    @DisplayName("評估派生指標 - MACD 正向旗標")
    void evaluate_MacdPositiveFlags() {
        // Given
        String expression = "macd_positive && macd_signal_positive";

        // When
        RuleEvaluationService.EvaluationResult result =
            evaluationService.evaluate(expression, testCard, testSnapshot, testIndicator);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isMatched()).isTrue();
    }

    @Test
    @DisplayName("評估量比條件")
    void evaluate_VolumeRatioCondition() {
        // Given
        String expression = "volumeRatio >= 1.5";

        // When
        RuleEvaluationService.EvaluationResult result =
            evaluationService.evaluate(expression, testCard, testSnapshot, testIndicator);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isMatched()).isTrue();
    }

    @Test
    @DisplayName("評估多重條件 - 黃金交叉")
    void evaluate_GoldenCross() {
        // Given
        String expression = "ma5 > ma20 && rsi < 70";

        // When
        RuleEvaluationService.EvaluationResult result =
            evaluationService.evaluate(expression, testCard, testSnapshot, testIndicator);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isMatched()).isTrue();
    }

    @Test
    @DisplayName("評估價格變化百分比條件")
    void evaluate_ChangePercentCondition() {
        // Given
        String expression = "changePercent > 0";

        // When
        RuleEvaluationService.EvaluationResult result =
            evaluationService.evaluate(expression, testCard, testSnapshot, testIndicator);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isMatched()).isTrue();
    }

    @Test
    @DisplayName("評估無效表達式 - 失敗")
    void evaluate_InvalidExpression_Failure() {
        // Given
        String expression = "invalid expression ***";

        // When
        RuleEvaluationService.EvaluationResult result =
            evaluationService.evaluate(expression, testCard, testSnapshot, testIndicator);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isNotNull();
    }

    @Test
    @DisplayName("評估 null 股票快照")
    void evaluate_NullSnapshot() {
        // Given
        String expression = "price > 500";

        // When
        RuleEvaluationService.EvaluationResult result =
            evaluationService.evaluate(expression, testCard, null, null);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isMatched()).isFalse();
    }

    @Test
    @DisplayName("驗證表達式 - 有效")
    void validateExpression_Valid() {
        // Given
        String expression = "price > 100 && volume > 1000000";

        // When
        boolean isValid = evaluationService.validateExpression(expression);

        // Then
        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("驗證表達式 - 無效")
    void validateExpression_Invalid() {
        // Given
        String expression = "invalid ***";

        // When
        boolean isValid = evaluationService.validateExpression(expression);

        // Then
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("評估條件 - 檢查所有變數")
    void evaluate_CheckAllVariables() {
        // Given
        String expression = "true";  // 總是為真的表達式

        // When
        RuleEvaluationService.EvaluationResult result =
            evaluationService.evaluate(expression, testCard, testSnapshot, testIndicator);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getVariables()).isNotEmpty();

        // 檢查股票快照變數
        assertThat(result.getVariables()).containsKeys(
            "price", "currentPrice", "openPrice", "highPrice", "lowPrice",
            "volume", "changePercent", "previousClose"
        );

        // 檢查技術指標變數
        assertThat(result.getVariables()).containsKeys(
            "ma5", "ma10", "ma20", "ma60",
            "rsi", "rsi14",
            "macd", "macdLine", "macdSignal", "macdHistogram",
            "kdK", "kdD"
        );
    }
}
