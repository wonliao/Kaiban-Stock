package com.kanban.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kanban.domain.entity.Card;
import com.kanban.domain.entity.StockSnapshot;
import com.kanban.domain.entity.TechnicalIndicator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 規則評估服務 - 使用 SpEL 評估規則條件
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RuleEvaluationService {

    private final ExpressionParser parser = new SpelExpressionParser();
    private final ObjectMapper objectMapper;

    /**
     * 評估規則條件
     *
     * @param conditionExpression SpEL 條件表達式
     * @param card 卡片資料
     * @param stockSnapshot 股票快照
     * @param technicalIndicator 技術指標
     * @return 評估結果及上下文資料
     */
    public EvaluationResult evaluate(
            String conditionExpression,
            Card card,
            StockSnapshot stockSnapshot,
            TechnicalIndicator technicalIndicator) {

        try {
            // 準備評估上下文
            EvaluationContext evalContext = prepareEvaluationContext(
                card, stockSnapshot, technicalIndicator
            );

            // 建立 SpEL 評估上下文
            StandardEvaluationContext context = new StandardEvaluationContext(evalContext);

            // 解析並評估表達式
            Expression expression = parser.parseExpression(conditionExpression);
            Boolean result = expression.getValue(context, Boolean.class);

            log.debug("規則評估: expression={}, result={}", conditionExpression, result);

            // 將評估上下文轉為 Map 用於返回
            Map<String, Object> variables = convertContextToMap(evalContext);

            return EvaluationResult.builder()
                .success(true)
                .matched(Boolean.TRUE.equals(result))
                .variables(variables)
                .expression(conditionExpression)
                .build();

        } catch (Exception e) {
            log.error("規則評估失敗: expression={}, error={}",
                     conditionExpression, e.getMessage(), e);

            return EvaluationResult.builder()
                .success(false)
                .matched(false)
                .errorMessage(e.getMessage())
                .expression(conditionExpression)
                .build();
        }
    }

    /**
     * 準備評估上下文
     */
    private EvaluationContext prepareEvaluationContext(
            Card card,
            StockSnapshot stockSnapshot,
            TechnicalIndicator technicalIndicator) {

        EvaluationContext context = new EvaluationContext();

        // 卡片資料
        if (card != null) {
            context.setStockCode(card.getStockCode());
            context.setStockName(card.getStockName());
            context.setCardStatus(card.getStatus().name());
        }

        // 股票快照資料
        if (stockSnapshot != null) {
            context.setPrice(stockSnapshot.getCurrentPrice());
            context.setCurrentPrice(stockSnapshot.getCurrentPrice());
            context.setOpenPrice(stockSnapshot.getOpenPrice());
            context.setHighPrice(stockSnapshot.getHighPrice());
            context.setLowPrice(stockSnapshot.getLowPrice());
            context.setVolume(stockSnapshot.getVolume());
            context.setChangePercent(stockSnapshot.getChangePercent());
            context.setPreviousClose(stockSnapshot.getPreviousClose());

            // 計算價格變化
            if (stockSnapshot.getCurrentPrice() != null && stockSnapshot.getPreviousClose() != null) {
                BigDecimal change = stockSnapshot.getCurrentPrice()
                    .subtract(stockSnapshot.getPreviousClose());
                context.setChange(change);
            }

            // 計算平均成交量（如果需要）
            if (stockSnapshot.getVolume() != null) {
                context.setAvgVolume(stockSnapshot.getVolume()); // 簡化版本
            }
        }

        // 技術指標資料
        if (technicalIndicator != null) {
            context.setMa5(technicalIndicator.getMa5());
            context.setMa10(technicalIndicator.getMa10());
            context.setMa20(technicalIndicator.getMa20());
            context.setMa60(technicalIndicator.getMa60());
            context.setRsi(technicalIndicator.getRsi14());
            context.setRsi14(technicalIndicator.getRsi14());
            context.setMacd(technicalIndicator.getMacdLine());
            context.setMacdLine(technicalIndicator.getMacdLine());
            context.setMacdSignal(technicalIndicator.getMacdSignal());
            context.setMacdHistogram(technicalIndicator.getMacdHistogram());
            context.setKValue(technicalIndicator.getKdK());
            context.setKdK(technicalIndicator.getKdK());
            context.setDValue(technicalIndicator.getKdD());
            context.setKdD(technicalIndicator.getKdD());
            context.setVolumeRatio(technicalIndicator.getVolumeRatio());

            // 計算額外的技術指標衍生值
            if (technicalIndicator.getMa5() != null && technicalIndicator.getMa20() != null) {
                context.setMa5_ma20_diff(
                    technicalIndicator.getMa5().subtract(technicalIndicator.getMa20()));
            }

            if (technicalIndicator.getMacdLine() != null && technicalIndicator.getMacdSignal() != null) {
                context.setMacd_positive(
                    technicalIndicator.getMacdLine().compareTo(BigDecimal.ZERO) > 0);
                context.setMacd_signal_positive(
                    technicalIndicator.getMacdSignal().compareTo(BigDecimal.ZERO) > 0);
            }
        }

        return context;
    }

    /**
     * 將評估上下文轉換為 Map
     */
    private Map<String, Object> convertContextToMap(EvaluationContext context) {
        Map<String, Object> variables = new HashMap<>();

        variables.put("stockCode", context.getStockCode());
        variables.put("stockName", context.getStockName());
        variables.put("cardStatus", context.getCardStatus());
        variables.put("price", context.getPrice());
        variables.put("currentPrice", context.getCurrentPrice());
        variables.put("openPrice", context.getOpenPrice());
        variables.put("highPrice", context.getHighPrice());
        variables.put("lowPrice", context.getLowPrice());
        variables.put("volume", context.getVolume());
        variables.put("changePercent", context.getChangePercent());
        variables.put("previousClose", context.getPreviousClose());
        variables.put("change", context.getChange());
        variables.put("avgVolume", context.getAvgVolume());
        variables.put("ma5", context.getMa5());
        variables.put("ma10", context.getMa10());
        variables.put("ma20", context.getMa20());
        variables.put("ma60", context.getMa60());
        variables.put("rsi", context.getRsi());
        variables.put("rsi14", context.getRsi14());
        variables.put("macd", context.getMacd());
        variables.put("macdLine", context.getMacdLine());
        variables.put("macdSignal", context.getMacdSignal());
        variables.put("macdHistogram", context.getMacdHistogram());
        variables.put("kValue", context.getKValue());
        variables.put("kdK", context.getKdK());
        variables.put("dValue", context.getDValue());
        variables.put("kdD", context.getKdD());
        variables.put("volumeRatio", context.getVolumeRatio());

        return variables;
    }

    /**
     * 驗證 SpEL 表達式語法
     */
    public boolean validateExpression(String expression) {
        try {
            parser.parseExpression(expression);
            return true;
        } catch (Exception e) {
            log.warn("SpEL 表達式驗證失敗: expression={}, error={}",
                    expression, e.getMessage());
            return false;
        }
    }

    /**
     * 評估結果
     */
    public static class EvaluationResult {
        private boolean success;
        private boolean matched;
        private Map<String, Object> variables;
        private String expression;
        private String errorMessage;

        public static EvaluationResultBuilder builder() {
            return new EvaluationResultBuilder();
        }

        public boolean isSuccess() {
            return success;
        }

        public boolean isMatched() {
            return matched;
        }

        public Map<String, Object> getVariables() {
            return variables;
        }

        public String getExpression() {
            return expression;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public String toJson() {
            try {
                return new ObjectMapper().writeValueAsString(this);
            } catch (Exception e) {
                return "{}";
            }
        }

        public static class EvaluationResultBuilder {
            private boolean success;
            private boolean matched;
            private Map<String, Object> variables;
            private String expression;
            private String errorMessage;

            public EvaluationResultBuilder success(boolean success) {
                this.success = success;
                return this;
            }

            public EvaluationResultBuilder matched(boolean matched) {
                this.matched = matched;
                return this;
            }

            public EvaluationResultBuilder variables(Map<String, Object> variables) {
                this.variables = variables;
                return this;
            }

            public EvaluationResultBuilder expression(String expression) {
                this.expression = expression;
                return this;
            }

            public EvaluationResultBuilder errorMessage(String errorMessage) {
                this.errorMessage = errorMessage;
                return this;
            }

            public EvaluationResult build() {
                EvaluationResult result = new EvaluationResult();
                result.success = this.success;
                result.matched = this.matched;
                result.variables = this.variables;
                result.expression = this.expression;
                result.errorMessage = this.errorMessage;
                return result;
            }
        }
    }
}
