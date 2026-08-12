-- ============================================================
-- V70__add_missing_tenant_indexes.sql
-- Description: Add any missing tenant_id indexes for performance
-- ============================================================

-- Additional indexes for user_activity_log (already has tenant_id from V59)
CREATE INDEX IF NOT EXISTS idx_user_activity_log_tenant_action ON user_activity_log(tenant_id, action);
CREATE INDEX IF NOT EXISTS idx_user_activity_log_tenant_created ON user_activity_log(tenant_id, created_at DESC);

-- Additional indexes for email_log (already has tenant_id from V32)
CREATE INDEX IF NOT EXISTS idx_email_log_tenant_status ON email_log(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_email_log_tenant_created ON email_log(tenant_id, created_at DESC);

-- Additional indexes for notification (already has tenant_id from V30)
CREATE INDEX IF NOT EXISTS idx_notification_tenant_user ON notification(tenant_id, user_id);
CREATE INDEX IF NOT EXISTS idx_notification_tenant_read ON notification(tenant_id, read);

-- Additional indexes for audit_event (already has tenant_id from V30)
CREATE INDEX IF NOT EXISTS idx_audit_event_tenant_entity ON audit_event(tenant_id, entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_audit_event_tenant_created ON audit_event(tenant_id, created_at DESC);

-- Additional indexes for transaction (already has tenant_id from V30)
CREATE INDEX IF NOT EXISTS idx_transaction_tenant_status ON transaction(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_transaction_tenant_created ON transaction(tenant_id, created_at DESC);

-- Additional indexes for invoice (already has tenant_id from V30)
CREATE INDEX IF NOT EXISTS idx_invoice_tenant_status ON invoice(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_invoice_tenant_merchant ON invoice(tenant_id, merchant_id);

-- Additional indexes for webhook_config (already has tenant_id from V31)
CREATE INDEX IF NOT EXISTS idx_webhook_config_tenant_event ON webhook_config(tenant_id, event_type);

-- Additional indexes for dlq_entry (already has tenant_id from V30)
CREATE INDEX IF NOT EXISTS idx_dlq_entry_tenant_status ON dlq_entry(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_dlq_entry_tenant_created ON dlq_entry(tenant_id, created_at DESC);