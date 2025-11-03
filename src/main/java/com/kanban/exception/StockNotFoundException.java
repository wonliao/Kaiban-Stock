package com.kanban.exception;

/**
 * 股票代碼不存在異常
 */
public class StockNotFoundException extends RuntimeException {
    
    private final String stockCode;
    
    public StockNotFoundException(String stockCode) {
        super("股票代碼不存在: " + stockCode);
        this.stockCode = stockCode;
    }
    
    public StockNotFoundException(String stockCode, String message) {
        super(message);
        this.stockCode = stockCode;
    }
    
    public String getStockCode() {
        return stockCode;
    }
}