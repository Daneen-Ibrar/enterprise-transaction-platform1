-- Reconciliation summary
CREATE TABLE IF NOT EXISTS reconciliation_record (
    id BIGSERIAL PRIMARY KEY,
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL,                 -- PASS, FAIL
    total_transactions INT NOT NULL,
    matched_transactions INT NOT NULL,
    mismatched_transactions INT NOT NULL,
    missing_ledger_count INT NOT NULL DEFAULT 0,
    amount_mismatch_count INT NOT NULL DEFAULT 0,
    details TEXT,                                -- optional summary text
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Reconciliation detail (per transaction discrepancy)
CREATE TABLE IF NOT EXISTS reconciliation_detail (
    id BIGSERIAL PRIMARY KEY,
    reconciliation_record_id BIGINT NOT NULL REFERENCES reconciliation_record(id) ON DELETE CASCADE,
    transaction_id BIGINT NOT NULL,
    discrepancy_type VARCHAR(30) NOT NULL,       -- MISSING_LEDGER, AMOUNT_MISMATCH, DUPLICATE
    expected_amount DECIMAL(19,2),
    actual_amount DECIMAL(19,2),
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_reconciliation_record_status ON reconciliation_record(status);
CREATE INDEX idx_reconciliation_detail_record_id ON reconciliation_detail(reconciliation_record_id);
CREATE INDEX idx_reconciliation_detail_transaction_id ON reconciliation_detail(transaction_id);