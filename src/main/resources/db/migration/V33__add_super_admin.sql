-- Add super_admin flag to app_user
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS super_admin BOOLEAN NOT NULL DEFAULT FALSE;

-- Mark the admin@test.com user as super admin (if it exists)
UPDATE app_user SET super_admin = TRUE WHERE email = 'admin@test.com';