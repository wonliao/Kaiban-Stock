-- Create users table
CREATE TABLE users (
    id VARCHAR(36) PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('VIEWER', 'EDITOR', 'ADMIN')),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create watchlists table
CREATE TABLE watchlists (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    max_size INTEGER DEFAULT 500,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create stock_snapshots table
CREATE TABLE stock_snapshots (
    stock_code VARCHAR(10) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    current_price DECIMAL(10,2),
    change_percent DECIMAL(5,2),
    volume BIGINT,
    ma5 DECIMAL(10,2),
    ma10 DECIMAL(10,2),
    ma20 DECIMAL(10,2),
    ma60 DECIMAL(10,2),
    rsi DECIMAL(5,2),
    kd_k DECIMAL(5,2),
    kd_d DECIMAL(5,2),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    data_source VARCHAR(50) DEFAULT 'TWSE-MCP',
    delay_minutes INTEGER DEFAULT 15
);

-- Create cards table
CREATE TABLE cards (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    watchlist_id VARCHAR(36) NOT NULL REFERENCES watchlists(id) ON DELETE CASCADE,
    stock_code VARCHAR(10) NOT NULL,
    stock_name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('WATCH', 'READY_TO_BUY', 'HOLD', 'SELL', 'ALERTS')),
    note TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, stock_code)
);

-- Create audit_logs table
CREATE TABLE audit_logs (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    card_id VARCHAR(36),
    action VARCHAR(50) NOT NULL,
    from_status VARCHAR(20),
    to_status VARCHAR(20),
    reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    trace_id VARCHAR(100),
    ip_address VARCHAR(45),
    user_agent TEXT
);

-- Create indexes for performance
CREATE INDEX idx_card_user_status ON cards(user_id, status, updated_at DESC);
CREATE INDEX idx_card_stock_code ON cards(stock_code);
CREATE INDEX idx_audit_log_user_time ON audit_logs(user_id, created_at DESC);
CREATE INDEX idx_stock_snapshot_updated ON stock_snapshots(updated_at DESC);

-- Insert default admin user (password: admin123)
INSERT INTO users (id, username, email, password_hash, role) VALUES 
('admin-user-id', 'admin', 'admin@kanban.com', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBaLyEOWN5EwNi', 'ADMIN');

-- Insert default watchlist for admin
INSERT INTO watchlists (id, user_id, name) VALUES 
('default-watchlist-id', 'admin-user-id', 'Default Watchlist');

-- Insert sample stock data
INSERT INTO stock_snapshots (stock_code, name, current_price, change_percent, volume, ma20, rsi) VALUES 
('2330', '台積電', 580.00, 2.5, 25000000, 575.00, 65.5),
('2317', '鴻海', 105.50, -1.2, 18000000, 107.20, 45.8),
('2454', '聯發科', 890.00, 3.8, 8500000, 850.00, 72.3);

-- Insert sample cards
INSERT INTO cards (id, user_id, watchlist_id, stock_code, stock_name, status) VALUES 
('card-1', 'admin-user-id', 'default-watchlist-id', '2330', '台積電', 'WATCH'),
('card-2', 'admin-user-id', 'default-watchlist-id', '2317', '鴻海', 'HOLD'),
('card-3', 'admin-user-id', 'default-watchlist-id', '2454', '聯發科', 'READY_TO_BUY');