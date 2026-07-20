-- Retry policy – defines max attempts and backoff strategy
CREATE TABLE IF NOT EXISTS retry_policy (
    id BIGSERIAL PRIMARY KEY,
    operation_type VARCHAR(50) NOT NULL UNIQUE,
    max_attempts INT NOT NULL,
    backoff_strategy VARCHAR(20) NOT NULL, -- FIXED, LINEAR, EXPONENTIAL
    base_delay_ms BIGINT NOT NULL,
    max_delay_ms BIGINT NOT NULL,
    jitter_enabled BOOLEAN DEFAULT TRUE,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Backoff policy (separate for flexibility)
CREATE TABLE IF NOT EXISTS backoff_policy (
    id BIGSERIAL PRIMARY KEY,
    retry_policy_id BIGINT NOT NULL REFERENCES retry_policy(id) ON DELETE CASCADE,
    multiplier DECIMAL(5,2),
    linear_increment_ms BIGINT,
    fixed_delay_ms BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Failure classification rules
CREATE TABLE IF NOT EXISTS failure_classification_rule (
    id BIGSERIAL PRIMARY KEY,
    condition_expression TEXT NOT NULL, -- SpEL or simple pattern
    category VARCHAR(30) NOT NULL,      -- TRANSIENT, PERMANENT, VALIDATION, INFRASTRUCTURE
    severity VARCHAR(20) NOT NULL,      -- LOW, MEDIUM, HIGH, CRITICAL
    action VARCHAR(20) NOT NULL,        -- RETRY, ROLLBACK, DLQ, IGNORE
    priority INT NOT NULL DEFAULT 0,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Dead Letter Queue (failed operations)
CREATE TABLE IF NOT EXISTS dlq_entry (
    id BIGSERIAL PRIMARY KEY,
    operation_type VARCHAR(50) NOT NULL,
    payload JSONB NOT NULL,
    failure_reason TEXT NOT NULL,
    failure_count INT NOT NULL DEFAULT 1,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, RETRYING, RESOLVED, FAILED
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Circuit breaker policy
CREATE TABLE IF NOT EXISTS circuit_breaker_policy (
    id BIGSERIAL PRIMARY KEY,
    operation_type VARCHAR(50) NOT NULL UNIQUE,
    failure_threshold INT NOT NULL,
    success_threshold INT NOT NULL,
    timeout_ms BIGINT NOT NULL,
    evaluation_window_sec INT NOT NULL,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Circuit breaker state (runtime)
CREATE TABLE IF NOT EXISTS circuit_breaker_state (
    id BIGSERIAL PRIMARY KEY,
    operation_type VARCHAR(50) NOT NULL UNIQUE,
    state VARCHAR(20) NOT NULL,          -- CLOSED, OPEN, HALF_OPEN
    failure_count INT NOT NULL DEFAULT 0,
    success_count INT NOT NULL DEFAULT 0,
    last_failure_time TIMESTAMP,
    last_success_time TIMESTAMP,
    opened_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Seed default policies
INSERT INTO retry_policy (operation_type, max_attempts, backoff_strategy, base_delay_ms, max_delay_ms, jitter_enabled)
VALUES
    ('PAYMENT', 3, 'EXPONENTIAL', 1000, 30000, true),
    ('REFUND', 3, 'EXPONENTIAL', 1000, 30000, true)
ON CONFLICT (operation_type) DO NOTHING;

INSERT INTO failure_classification_rule (condition_expression, category, severity, action, priority)
VALUES
    ('message contains "timeout"', 'TRANSIENT', 'MEDIUM', 'RETRY', 10),
    ('message contains "connection refused"', 'TRANSIENT', 'HIGH', 'RETRY', 20),
    ('message contains "SQL"', 'INFRASTRUCTURE', 'HIGH', 'RETRY', 30),
    ('message contains "validation"', 'VALIDATION', 'LOW', 'IGNORE', 40),
    ('message contains "permission"', 'VALIDATION', 'MEDIUM', 'ROLLBACK', 50)
ON CONFLICT DO NOTHING;

INSERT INTO circuit_breaker_policy (operation_type, failure_threshold, success_threshold, timeout_ms, evaluation_window_sec)
VALUES
    ('PAYMENT', 5, 3, 60000, 30),
    ('REFUND', 5, 3, 60000, 30)
ON CONFLICT (operation_type) DO NOTHING;

-- Initial circuit states (CLOSED)
INSERT INTO circuit_breaker_state (operation_type, state)
SELECT operation_type, 'CLOSED' FROM circuit_breaker_policy
ON CONFLICT (operation_type) DO NOTHING;