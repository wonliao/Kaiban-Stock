-- 建立規則表
CREATE TABLE IF NOT EXISTS rules (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    rule_type VARCHAR(20) NOT NULL DEFAULT 'CUSTOM',
    condition_expression TEXT NOT NULL,
    trigger_event VARCHAR(30) NOT NULL,
    target_status VARCHAR(20) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    cooldown_seconds INTEGER NOT NULL DEFAULT 3600,
    priority INTEGER NOT NULL DEFAULT 5,
    send_notification BOOLEAN NOT NULL DEFAULT TRUE,
    notification_template TEXT,
    tags TEXT,
    parameters TEXT,
    last_executed_at TIMESTAMP,
    trigger_count BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_rule_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 建立規則執行歷史表
CREATE TABLE IF NOT EXISTS rule_executions (
    id VARCHAR(36) PRIMARY KEY,
    rule_id VARCHAR(36) NOT NULL,
    card_id VARCHAR(36) NOT NULL,
    status VARCHAR(20) NOT NULL,
    previous_status VARCHAR(20),
    new_status VARCHAR(20),
    condition_result TEXT,
    stock_snapshot TEXT,
    message TEXT,
    notification_sent BOOLEAN NOT NULL DEFAULT FALSE,
    execution_time_ms BIGINT,
    executed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_execution_rule FOREIGN KEY (rule_id) REFERENCES rules(id) ON DELETE CASCADE,
    CONSTRAINT fk_execution_card FOREIGN KEY (card_id) REFERENCES cards(id) ON DELETE CASCADE
);

-- 建立索引
CREATE INDEX idx_rule_user_enabled ON rules(user_id, enabled, rule_type);
CREATE INDEX idx_rule_trigger_target ON rules(trigger_event, target_status);
CREATE INDEX idx_execution_rule_time ON rule_executions(rule_id, executed_at);
CREATE INDEX idx_execution_card_time ON rule_executions(card_id, executed_at);
CREATE INDEX idx_execution_status_time ON rule_executions(status, executed_at);

-- 建立使用者規則名稱唯一約束
CREATE UNIQUE INDEX idx_rule_user_name ON rules(user_id, name);

-- 建立註釋
COMMENT ON TABLE rules IS '規則表 - 儲存自動化規則定義';
COMMENT ON TABLE rule_executions IS '規則執行歷史表 - 記錄規則執行結果';

COMMENT ON COLUMN rules.condition_expression IS 'SpEL 表達式 - 用於評估規則條件';
COMMENT ON COLUMN rules.trigger_event IS '觸發事件類型: PRICE_CHANGE, VOLUME_SPIKE, TECHNICAL_INDICATOR, PRICE_ALERT, TIME_BASED';
COMMENT ON COLUMN rules.target_status IS '目標狀態 - 規則觸發後卡片應轉換的狀態';
COMMENT ON COLUMN rules.cooldown_seconds IS '冷卻時間（秒） - 防止重複觸發';
COMMENT ON COLUMN rules.priority IS '優先級 - 數字越小優先級越高';
COMMENT ON COLUMN rules.tags IS '規則標籤 - JSON 格式，用於分類和篩選';
COMMENT ON COLUMN rules.parameters IS '規則參數 - JSON 格式，用於儲存額外配置';

COMMENT ON COLUMN rule_executions.status IS '執行狀態: SUCCESS, FAILED, SKIPPED, COOLDOWN';
COMMENT ON COLUMN rule_executions.condition_result IS '條件評估結果 - JSON 格式';
COMMENT ON COLUMN rule_executions.stock_snapshot IS '觸發時的股票資料快照 - JSON 格式';
COMMENT ON COLUMN rule_executions.execution_time_ms IS '執行耗時（毫秒）';
