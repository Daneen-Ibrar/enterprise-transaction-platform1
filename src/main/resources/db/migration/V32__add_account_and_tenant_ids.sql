-- ============================================================
-- V32: Add account_id to ledger_entry, tenant_id to api_key and email_log
-- ============================================================

-- 1. Add account_id to ledger_entry
ALTER TABLE ledger_entry ADD COLUMN IF NOT EXISTS account_id BIGINT;
UPDATE ledger_entry SET account_id = 1 WHERE account_id IS NULL;
ALTER TABLE ledger_entry ALTER COLUMN account_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_ledger_entry_account_id ON ledger_entry(account_id);

-- 2. Add tenant_id to api_key
ALTER TABLE api_key ADD COLUMN IF NOT EXISTS tenant_id BIGINT REFERENCES tenant(id);
UPDATE api_key SET tenant_id = 1 WHERE tenant_id IS NULL;
ALTER TABLE api_key ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_api_key_tenant ON api_key(tenant_id);

-- 3. Add tenant_id to email_log
ALTER TABLE email_log ADD COLUMN IF NOT EXISTS tenant_id BIGINT REFERENCES tenant(id);
UPDATE email_log SET tenant_id = 1 WHERE tenant_id IS NULL;
ALTER TABLE email_log ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_email_log_tenant ON email_log(tenant_id);