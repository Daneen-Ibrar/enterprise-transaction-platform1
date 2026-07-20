-- Add tenant_id to all remaining core tables
ALTER TABLE webhook_config ADD COLUMN tenant_id BIGINT REFERENCES tenant(id);
ALTER TABLE approval_rule ADD COLUMN tenant_id BIGINT REFERENCES tenant(id);
ALTER TABLE refund_rule ADD COLUMN tenant_id BIGINT REFERENCES tenant(id);
ALTER TABLE suspicion_rule ADD COLUMN tenant_id BIGINT REFERENCES tenant(id);
ALTER TABLE feature_flag ADD COLUMN tenant_id BIGINT REFERENCES tenant(id);
ALTER TABLE private_message ADD COLUMN tenant_id BIGINT REFERENCES tenant(id);
ALTER TABLE idempotency_key ADD COLUMN tenant_id BIGINT REFERENCES tenant(id);

-- Seed default tenant (1) if not exists
INSERT INTO tenant (id, name, description, active) VALUES (1, 'default', 'Default tenant', true) ON CONFLICT DO NOTHING;

-- Update existing rows to default tenant
UPDATE webhook_config SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE approval_rule SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE refund_rule SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE suspicion_rule SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE feature_flag SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE private_message SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE idempotency_key SET tenant_id = 1 WHERE tenant_id IS NULL;

-- Make tenant_id NOT NULL
ALTER TABLE webhook_config ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE approval_rule ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE refund_rule ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE suspicion_rule ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE feature_flag ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE private_message ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE idempotency_key ALTER COLUMN tenant_id SET NOT NULL;

-- Add indexes
CREATE INDEX idx_webhook_config_tenant ON webhook_config(tenant_id);
CREATE INDEX idx_approval_rule_tenant ON approval_rule(tenant_id);
CREATE INDEX idx_refund_rule_tenant ON refund_rule(tenant_id);
CREATE INDEX idx_suspicion_rule_tenant ON suspicion_rule(tenant_id);
CREATE INDEX idx_feature_flag_tenant ON feature_flag(tenant_id);
CREATE INDEX idx_private_message_tenant ON private_message(tenant_id);
CREATE INDEX idx_idempotency_key_tenant ON idempotency_key(tenant_id);