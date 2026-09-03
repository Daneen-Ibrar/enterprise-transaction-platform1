-- ============================================================
-- V89: Create subscription_request table
-- ============================================================

CREATE TABLE IF NOT EXISTS subscription_request (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    customer_email VARCHAR(255) NOT NULL,
    plan_id BIGINT NOT NULL,
    plan_name VARCHAR(255) NOT NULL,
    merchant_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    notes TEXT,
    assigned_by BIGINT,
    assigned_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_subscription_request_plan FOREIGN KEY (plan_id) REFERENCES subscription_plan(id)
);

CREATE INDEX idx_subscription_request_tenant ON subscription_request(tenant_id);
CREATE INDEX idx_subscription_request_merchant ON subscription_request(merchant_id);
CREATE INDEX idx_subscription_request_customer ON subscription_request(customer_id);
CREATE INDEX idx_subscription_request_status ON subscription_request(status);

COMMENT ON TABLE subscription_request IS 'Subscription requests awaiting admin approval';
COMMENT ON COLUMN subscription_request.status IS 'PENDING, APPROVED, REJECTED, ASSIGNED';