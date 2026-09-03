-- ============================================================
-- V83: Create rate_limit_config and rate_limit_violation tables
-- ============================================================

-- Rate Limit Config Table
CREATE TABLE IF NOT EXISTS rate_limit_config (
    id BIGSERIAL PRIMARY KEY,
    api_key_prefix VARCHAR(50) NOT NULL,
    tenant_id BIGINT NOT NULL,
    payment_limit INT NOT NULL DEFAULT 100,
    payment_window_seconds INT NOT NULL DEFAULT 60,
    read_limit INT NOT NULL DEFAULT 200,
    read_window_seconds INT NOT NULL DEFAULT 60,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_rate_limit_config_tenant_api_key UNIQUE (tenant_id, api_key_prefix)
);

CREATE INDEX IF NOT EXISTS idx_rate_limit_config_tenant ON rate_limit_config(tenant_id);
CREATE INDEX IF NOT EXISTS idx_rate_limit_config_api_key ON rate_limit_config(api_key_prefix);

-- Rate Limit Violation Table
CREATE TABLE IF NOT EXISTS rate_limit_violation (
    id BIGSERIAL PRIMARY KEY,
    api_key_prefix VARCHAR(50) NOT NULL,
    tenant_id BIGINT NOT NULL,
    limit_type VARCHAR(20) NOT NULL,
    limit_value INT NOT NULL,
    actual_usage INT NOT NULL,
    request_path TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_rate_limit_violation_tenant ON rate_limit_violation(tenant_id);
CREATE INDEX IF NOT EXISTS idx_rate_limit_violation_api_key ON rate_limit_violation(api_key_prefix);
CREATE INDEX IF NOT EXISTS idx_rate_limit_violation_created ON rate_limit_violation(created_at DESC);