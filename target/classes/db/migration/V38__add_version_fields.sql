-- Add version fields for optimistic locking
ALTER TABLE transaction ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE invoice ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Create refund table
CREATE TABLE IF NOT EXISTS refund (
    id BIGSERIAL PRIMARY KEY,
    transaction_id BIGINT NOT NULL REFERENCES transaction(id),
    amount VARCHAR(255) NOT NULL, -- Money: amount|currency
    reason TEXT,
    status VARCHAR(20) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    processed_by BIGINT,
    processed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_refund_transaction_id ON refund(transaction_id);
CREATE INDEX idx_refund_status ON refund(status);
CREATE INDEX idx_refund_idempotency_key ON refund(idempotency_key);