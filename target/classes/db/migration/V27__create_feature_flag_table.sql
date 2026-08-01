CREATE TABLE IF NOT EXISTS feature_flag (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    description TEXT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Seed default flags
INSERT INTO feature_flag (name, enabled, description) VALUES
    ('REFUNDS', true, 'Enable refund processing'),
    ('WEBHOOKS', true, 'Send webhook events'),
    ('SUSPICION_DETECTION', true, 'Flag suspicious invoices'),
    ('TWO_FACTOR_AUTH', false, 'Two-factor authentication requirement'),
    ('RELIABILITY_POLICIES', true, 'Apply retry and circuit breaker policies')
ON CONFLICT (name) DO NOTHING;