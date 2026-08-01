-- Permission table – defines all protected resources and actions
CREATE TABLE IF NOT EXISTS permission (
    id BIGSERIAL PRIMARY KEY,
    resource VARCHAR(100) NOT NULL,
    action VARCHAR(50) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(resource, action)
);

-- Role‑permission mapping (many‑to‑many)
CREATE TABLE IF NOT EXISTS role_permission (
    role_id BIGINT NOT NULL REFERENCES role(id),
    permission_id BIGINT NOT NULL REFERENCES permission(id),
    granted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    granted_by BIGINT NULL,  -- optional admin actor
    PRIMARY KEY (role_id, permission_id)
);

-- Seed standard permissions (these are the tokens used in @PreAuthorize)
INSERT INTO permission (resource, action, description) VALUES
    ('invoice', 'create', 'Create a new invoice'),
    ('invoice', 'view_own', 'View own invoices'),
    ('invoice', 'view_all', 'View all invoices'),
    ('invoice', 'approve', 'Approve high‑value invoices'),
    ('payment', 'process', 'Process a payment'),
    ('payment', 'refund', 'Issue a refund'),
    ('audit', 'view', 'View audit logs'),
    ('audit', 'verify', 'Verify audit integrity'),
    ('reconciliation', 'run', 'Run reconciliation jobs'),
    ('reconciliation', 'view_report', 'View reconciliation reports')
ON CONFLICT (resource, action) DO NOTHING;

-- Assign permissions to roles (seed data – can be extended later)
-- CUSTOMER: invoice:view_own, payment:process
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r, permission p
WHERE r.name = 'CUSTOMER'
  AND p.resource = 'invoice' AND p.action = 'view_own'
UNION ALL
SELECT r.id, p.id
FROM role r, permission p
WHERE r.name = 'CUSTOMER'
  AND p.resource = 'payment' AND p.action = 'process'
ON CONFLICT DO NOTHING;

-- MERCHANT: invoice:create, invoice:view_own
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r, permission p
WHERE r.name = 'MERCHANT'
  AND ( (p.resource = 'invoice' AND p.action = 'create')
     OR (p.resource = 'invoice' AND p.action = 'view_own') )
ON CONFLICT DO NOTHING;

-- ADMIN: all permissions (except maybe audit:verify? but we give all for simplicity)
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r, permission p
WHERE r.name = 'ADMIN'
ON CONFLICT DO NOTHING;

-- AUDITOR: audit:view, audit:verify, reconciliation:view_report
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r, permission p
WHERE r.name = 'AUDITOR'
  AND ( (p.resource = 'audit' AND p.action IN ('view', 'verify'))
     OR (p.resource = 'reconciliation' AND p.action = 'view_report') )
ON CONFLICT DO NOTHING;