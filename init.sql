USE trading_bot_db;

-- Create tables
CREATE TABLE IF NOT EXISTS account (
    id INT NOT NULL DEFAULT 1,
    balance DECIMAL(20, 8) NOT NULL DEFAULT 10000.00,
    initial_balance DECIMAL(20, 8) NOT NULL DEFAULT 10000.00,
    mode VARCHAR(20) NOT NULL DEFAULT 'TRAINING' CHECK (mode IN ('TRAINING', 'TRADING')),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(id, mode)
);

CREATE TABLE IF NOT EXISTS portfolio (
    account_id INTEGER REFERENCES account(id),
    symbol VARCHAR(20) NOT NULL,
    quantity DECIMAL(20, 8) NOT NULL DEFAULT 0,
    average_buy_price DECIMAL(20, 8) NOT NULL DEFAULT 0,
    mode VARCHAR(20) NOT NULL DEFAULT 'TRAINING' CHECK (mode IN ('TRAINING', 'TRADING')),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(account_id, symbol, mode)
);

CREATE TABLE IF NOT EXISTS trades (
    id INT AUTO_INCREMENT PRIMARY KEY,
    account_id INTEGER REFERENCES account(id),
    symbol VARCHAR(20) NOT NULL,
    trade_type VARCHAR(10) NOT NULL CHECK (trade_type IN ('BUY', 'SELL')),
    quantity DECIMAL(20, 8) NOT NULL,
    price DECIMAL(20, 8) NOT NULL,
    total_value DECIMAL(20, 8) NOT NULL,
    profit_loss DECIMAL(20, 8) DEFAULT 0,
    timestamp TIMESTAMP NOT NULL,
    mode VARCHAR(20) NOT NULL CHECK (mode IN ('TRAINING', 'TRADING')),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS price_history (
    symbol VARCHAR(20) NOT NULL,
    price DECIMAL(20, 8) NOT NULL,
    mode VARCHAR(20) NOT NULL CHECK (mode IN ('TRAINING', 'TRADING')),
    timestamp TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(symbol, mode, timestamp)
);

CREATE TABLE IF NOT EXISTS bot_status (
    id INT AUTO_INCREMENT PRIMARY KEY,
    is_running BOOLEAN DEFAULT FALSE,
    mode VARCHAR(20) NOT NULL DEFAULT 'TRAINING' CHECK (mode IN ('TRAINING', 'TRADING')),
    last_run TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance
CREATE INDEX idx_trades_account_id ON trades(account_id);
CREATE INDEX idx_trades_timestamp ON trades(timestamp);
CREATE INDEX idx_price_history_symbol_timestamp ON price_history(symbol, timestamp);
CREATE INDEX idx_portfolio_account_id ON portfolio(account_id);

-- Insert initial account for training
INSERT INTO account (balance, initial_balance, mode) VALUES (10000.00, 10000.00, 'TRAINING');

-- Insert initial account for trading
INSERT INTO account (balance, initial_balance, mode) VALUES (10000.00, 10000.00, 'TRADING');

-- Insert initial bot status
INSERT INTO bot_status (is_running, mode) VALUES (FALSE, 'TRAINING');

-- Account table trigger
CREATE TRIGGER update_account_updated_at
BEFORE UPDATE ON account
FOR EACH ROW
    SET NEW.updated_at = CURRENT_TIMESTAMP;

-- Portfolio table trigger
CREATE TRIGGER update_portfolio_updated_at
BEFORE UPDATE ON portfolio
FOR EACH ROW
    SET NEW.updated_at = CURRENT_TIMESTAMP;

-- Bot status table trigger
CREATE TRIGGER update_bot_status_updated_at
BEFORE UPDATE ON bot_status
FOR EACH ROW
    SET NEW.updated_at = CURRENT_TIMESTAMP;