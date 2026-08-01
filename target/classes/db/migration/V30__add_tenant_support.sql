-- Create tenant table
CREATE TABLE IF NOT EXISTS tenant (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

-- Add tenant_id to all core tables
ALTER TABLE app_user ADD COLUMN tenant_id BIGINT REFERENCES tenant(id);
ALTER TABLE invoice ADD COLUMN tenant_id BIGINT REFERENCES tenant(id);
ALTER TABLE transaction ADD COLUMN tenant_id BIGINT REFERENCES tenant(id);
ALTER TABLE ledger_entry ADD COLUMN tenant_id BIGINT REFERENCES tenant(id);
ALTER TABLE audit_event ADD COLUMN tenant_id BIGINT REFERENCES tenant(id);
ALTER TABLE reconciliation_record ADD COLUMN tenant_id BIGINT REFERENCES tenant(id);
ALTER TABLE notification ADD COLUMN tenant_id BIGINT REFERENCES tenant(id);
ALTER TABLE dlq_entry ADD COLUMN tenant_id BIGINT REFERENCES tenant(id);

-- Seed default tenant
INSERT INTO tenant (id, name, description, active) VALUES (1, 'default', 'Default tenant', true);

-- Update existing records to default tenant
UPDATE app_user SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE invoice SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE transaction SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE ledger_entry SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE audit_event SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE reconciliation_record SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE notification SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE dlq_entry SET tenant_id = 1 WHERE tenant_id IS NULL;

-- Make tenant_id NOT NULL
ALTER TABLE app_user ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE invoice ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE transaction ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE ledger_entry ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE audit_event ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE reconciliation_record ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE notification ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE dlq_entry ALTER COLUMN tenant_id SET NOT NULL;

-- Create indexes
CREATE INDEX idx_app_user_tenant ON app_user(tenant_id);
CREATE INDEX idx_invoice_tenant ON invoice(tenant_id);
CREATE INDEX idx_transaction_tenant ON transaction(tenant_id);
CREATE INDEX idx_ledger_entry_tenant ON ledger_entry(tenant_id);
CREATE INDEX idx_audit_event_tenant ON audit_event(tenant_id);

SELECT setval('tenant_id_seq', (SELECT MAX(id) FROM tenant));