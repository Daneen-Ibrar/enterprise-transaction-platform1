CREATE TABLE IF NOT EXISTS webhook_config (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    url VARCHAR(500) NOT NULL,
    event_type VARCHAR(50) NOT NULL,   -- e.g., TRANSACTION_SETTLED, REFUND_PROCESSED
    active BOOLEAN NOT NULL DEFAULT TRUE,
    retry_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    retry_max_attempts INT DEFAULT 3,
    timeout_ms INT DEFAULT 5000,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE INDEX idx_webhook_event_type ON webhook_config(event_type);
CREATE INDEX idx_webhook_active ON webhook_config(active);