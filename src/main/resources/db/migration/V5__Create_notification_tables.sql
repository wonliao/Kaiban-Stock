-- 建立通知表
CREATE TABLE IF NOT EXISTS notifications (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    title VARCHAR(255) NOT NULL,
    message TEXT,
    type VARCHAR(20) NOT NULL DEFAULT 'INFO',
    rule_id VARCHAR(36),
    card_id VARCHAR(36),
    stock_code VARCHAR(10),
    metadata TEXT,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_notification_rule FOREIGN KEY (rule_id) REFERENCES rules(id) ON DELETE SET NULL,
    CONSTRAINT fk_notification_card FOREIGN KEY (card_id) REFERENCES cards(id) ON DELETE SET NULL
);

-- 建立索引
CREATE INDEX idx_notification_user_created ON notifications(user_id, created_at DESC);
CREATE INDEX idx_notification_user_read ON notifications(user_id, is_read, created_at DESC);
CREATE INDEX idx_notification_type ON notifications(type, created_at DESC);
CREATE INDEX idx_notification_rule ON notifications(rule_id);
CREATE INDEX idx_notification_card ON notifications(card_id);

-- 建立註釋
COMMENT ON TABLE notifications IS '通知表 - 儲存使用者通知';

COMMENT ON COLUMN notifications.type IS '通知類型: INFO, SUCCESS, WARNING, ERROR, RULE_TRIGGERED';
COMMENT ON COLUMN notifications.rule_id IS '關聯的規則 ID';
COMMENT ON COLUMN notifications.card_id IS '關聯的卡片 ID';
COMMENT ON COLUMN notifications.stock_code IS '關聯的股票代碼';
COMMENT ON COLUMN notifications.metadata IS '額外資料（JSON 格式）';
COMMENT ON COLUMN notifications.is_read IS '是否已讀';
COMMENT ON COLUMN notifications.read_at IS '讀取時間';
