-- Add login lock columns to app_user
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS failed_login_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS account_locked BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS lock_expiry TIMESTAMP;