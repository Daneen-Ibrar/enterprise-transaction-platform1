-- ============================================================
-- V82: Add Roles and Super Admin
-- ============================================================

-- Insert roles if they don't exist
INSERT INTO role (name, description, created_at)
SELECT 'SUPER_ADMIN', 'Super Administrator - Full system access', NOW()
WHERE NOT EXISTS (SELECT 1 FROM role WHERE name = 'SUPER_ADMIN');

INSERT INTO role (name, description, created_at)
SELECT 'MERCHANT_ADMIN', 'Merchant Administrator - Manage merchants', NOW()
WHERE NOT EXISTS (SELECT 1 FROM role WHERE name = 'MERCHANT_ADMIN');

INSERT INTO role (name, description, created_at)
SELECT 'MERCHANT', 'Merchant - Create and manage invoices', NOW()
WHERE NOT EXISTS (SELECT 1 FROM role WHERE name = 'MERCHANT');

INSERT INTO role (name, description, created_at)
SELECT 'CUSTOMER', 'Customer - Pay invoices', NOW()
WHERE NOT EXISTS (SELECT 1 FROM role WHERE name = 'CUSTOMER');

INSERT INTO role (name, description, created_at)
SELECT 'AUDITOR', 'Auditor - View audit trails', NOW()
WHERE NOT EXISTS (SELECT 1 FROM role WHERE name = 'AUDITOR');

-- Update existing admin users to SUPER_ADMIN
UPDATE app_user 
SET super_admin = true 
WHERE email IN ('admin@test.com', 'superadmin@test.com');

-- Assign SUPER_ADMIN role to admin users
INSERT INTO user_role (user_id, role_id)
SELECT u.id, r.id
FROM app_user u
CROSS JOIN role r
WHERE u.email IN ('admin@test.com', 'superadmin@test.com')
AND r.name = 'SUPER_ADMIN'
ON CONFLICT (user_id, role_id) DO NOTHING;

-- Assign MERCHANT_ADMIN role to merchant admin users
INSERT INTO user_role (user_id, role_id)
SELECT u.id, r.id
FROM app_user u
CROSS JOIN role r
WHERE u.email = 'merchantadmin@test.com'
AND r.name = 'MERCHANT_ADMIN'
ON CONFLICT (user_id, role_id) DO NOTHING;

-- Assign MERCHANT role to merchant users
INSERT INTO user_role (user_id, role_id)
SELECT u.id, r.id
FROM app_user u
CROSS JOIN role r
WHERE u.email IN ('merchant@test.com', 'merchantadmin@test.com')
AND r.name = 'MERCHANT'
ON CONFLICT (user_id, role_id) DO NOTHING;

-- Assign CUSTOMER role to customer users
INSERT INTO user_role (user_id, role_id)
SELECT u.id, r.id
FROM app_user u
CROSS JOIN role r
WHERE u.email = 'customer@test.com'
AND r.name = 'CUSTOMER'
ON CONFLICT (user_id, role_id) DO NOTHING;

-- Assign AUDITOR role to auditor users
INSERT INTO user_role (user_id, role_id)
SELECT u.id, r.id
FROM app_user u
CROSS JOIN role r
WHERE u.email = 'auditor@test.com'
AND r.name = 'AUDITOR'
ON CONFLICT (user_id, role_id) DO NOTHING;