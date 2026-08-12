-- V77__add_xero_auto_sync_flag.sql
INSERT INTO feature_flag (name, enabled, description, tenant_id, updated_at)
SELECT 'XERO_AUTO_SYNC', true, 'Automatically sync invoices to Xero when approved', t.id, NOW()
FROM tenant t
WHERE t.id = 1
ON CONFLICT (name, tenant_id) DO NOTHING;

INSERT INTO feature_flag (name, enabled, description, tenant_id, updated_at)
SELECT 'XERO_AUTO_SYNC', true, 'Automatically sync invoices to Xero when approved', t.id, NOW()
FROM tenant t
WHERE t.id = 2
ON CONFLICT (name, tenant_id) DO NOTHING;