CREATE TABLE recurring_schedule (
    id BIGSERIAL PRIMARY KEY,
    merchant_id BIGINT NOT NULL,
    customer_email VARCHAR(255) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    description TEXT NOT NULL,
    frequency VARCHAR(10) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE,
    next_run_date DATE NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    tenant_id BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_recurring_schedule_merchant ON recurring_schedule(merchant_id);
CREATE INDEX idx_recurring_schedule_next_run ON recurring_schedule(active, next_run_date, tenant_id);