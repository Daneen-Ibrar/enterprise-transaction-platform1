-- ============================================================
-- V53__create_generated_report_table.sql
-- Description: Table to store generated reports (PDF/CSV)
-- ============================================================

CREATE TABLE IF NOT EXISTS generated_report (
    id BIGSERIAL PRIMARY KEY,
    filename VARCHAR(255) NOT NULL,
    report_type VARCHAR(20) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    generated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    content BYTEA NOT NULL,
    content_type VARCHAR(50) NOT NULL,
    tenant_id BIGINT NOT NULL
);

CREATE INDEX idx_generated_report_tenant ON generated_report(tenant_id);
CREATE INDEX idx_generated_report_type ON generated_report(report_type);
CREATE INDEX idx_generated_report_generated_at ON generated_report(generated_at DESC);