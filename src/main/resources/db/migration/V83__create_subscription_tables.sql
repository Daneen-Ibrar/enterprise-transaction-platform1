-- ============================================================
-- V87: Create subscription tables
-- ============================================================

-- Subscription Plan Table
CREATE TABLE IF NOT EXISTS subscription_plan (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    billing_period VARCHAR(20) NOT NULL,
    price DECIMAL(19,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'GBP',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    trial_days INT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_subscription_plan_tenant_name UNIQUE (tenant_id, name)
);

-- Customer Subscription Table
CREATE TABLE IF NOT EXISTS customer_subscription (
    id BIGSERIAL PRIMARY KEY,
    plan_id BIGINT NOT NULL REFERENCES subscription_plan(id),
    customer_id BIGINT NOT NULL,
    customer_email VARCHAR(255) NOT NULL,
    merchant_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    start_date TIMESTAMP NOT NULL,
    end_date TIMESTAMP,
    next_billing_date TIMESTAMP NOT NULL,
    last_billing_date TIMESTAMP,
    trial_end_date TIMESTAMP,
    cancelled_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    version BIGINT NOT NULL DEFAULT 0
);

-- Indexes
CREATE INDEX idx_subscription_plan_tenant ON subscription_plan(tenant_id);
CREATE INDEX idx_subscription_plan_active ON subscription_plan(is_active);
CREATE INDEX idx_customer_subscription_tenant ON customer_subscription(tenant_id);
CREATE INDEX idx_customer_subscription_customer ON customer_subscription(customer_id);
CREATE INDEX idx_customer_subscription_merchant ON customer_subscription(merchant_id);
CREATE INDEX idx_customer_subscription_status ON customer_subscription(status);
CREATE INDEX idx_customer_subscription_next_billing ON customer_subscription(next_billing_date);

-- Seed default subscription plans
INSERT INTO subscription_plan (name, description, billing_period, price, currency, is_active, trial_days, tenant_id) VALUES
('Basic', 'Basic subscription plan with essential features', 'MONTHLY', 9.99, 'GBP', TRUE, 14, 1),
('Pro', 'Pro subscription plan with advanced features', 'MONTHLY', 29.99, 'GBP', TRUE, 14, 1),
('Enterprise', 'Enterprise plan with premium support', 'YEARLY', 299.99, 'GBP', TRUE, 30, 1)
ON CONFLICT (tenant_id, name) DO NOTHING;