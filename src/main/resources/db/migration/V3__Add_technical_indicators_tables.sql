-- 新增歷史價格表
CREATE TABLE historical_prices (
    id VARCHAR(36) PRIMARY KEY,
    stock_code VARCHAR(10) NOT NULL,
    trade_date DATE NOT NULL,
    open_price DECIMAL(10,2),
    high_price DECIMAL(10,2) NOT NULL,
    low_price DECIMAL(10,2) NOT NULL,
    close_price DECIMAL(10,2) NOT NULL,
    volume BIGINT NOT NULL,
    adjusted_close DECIMAL(10,2),
    data_source VARCHAR(50) DEFAULT 'TWSE-MCP',
    UNIQUE(stock_code, trade_date)
);

-- 新增技術指標表
CREATE TABLE technical_indicators (
    id VARCHAR(36) PRIMARY KEY,
    stock_code VARCHAR(10) NOT NULL,
    calculation_date TIMESTAMP NOT NULL,
    ma5 DECIMAL(10,2),
    ma10 DECIMAL(10,2),
    ma20 DECIMAL(10,2),
    ma60 DECIMAL(10,2),
    rsi_14 DECIMAL(5,2),
    kd_k DECIMAL(5,2),
    kd_d DECIMAL(5,2),
    macd_line DECIMAL(10,4),
    macd_signal DECIMAL(10,4),
    macd_histogram DECIMAL(10,4),
    volume_ma5 BIGINT,
    volume_ma20 BIGINT,
    volume_ratio DECIMAL(5,2),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    data_points_count INTEGER,
    calculation_source VARCHAR(50) DEFAULT 'INTERNAL'
);

-- 更新股票快照表，新增 OHLC 欄位
ALTER TABLE stock_snapshots 
ADD COLUMN open_price DECIMAL(10,2),
ADD COLUMN high_price DECIMAL(10,2),
ADD COLUMN low_price DECIMAL(10,2),
ADD COLUMN previous_close DECIMAL(10,2);

-- 建立索引
CREATE INDEX idx_historical_stock_date ON historical_prices(stock_code, trade_date DESC);
CREATE INDEX idx_historical_date ON historical_prices(trade_date DESC);
CREATE INDEX idx_technical_stock_date ON technical_indicators(stock_code, calculation_date DESC);
CREATE INDEX idx_technical_updated ON technical_indicators(updated_at DESC);

-- 新增註解
COMMENT ON TABLE historical_prices IS '股票歷史價格資料表';
COMMENT ON TABLE technical_indicators IS '技術指標計算結果表';
COMMENT ON COLUMN historical_prices.stock_code IS '股票代碼';
COMMENT ON COLUMN historical_prices.trade_date IS '交易日期';
COMMENT ON COLUMN historical_prices.volume IS '成交量';
COMMENT ON COLUMN technical_indicators.ma5 IS '5日移動平均線';
COMMENT ON COLUMN technical_indicators.ma20 IS '20日移動平均線';
COMMENT ON COLUMN technical_indicators.rsi_14 IS '14日RSI指標';
COMMENT ON COLUMN technical_indicators.kd_k IS 'KD指標K值';
COMMENT ON COLUMN technical_indicators.kd_d IS 'KD指標D值';