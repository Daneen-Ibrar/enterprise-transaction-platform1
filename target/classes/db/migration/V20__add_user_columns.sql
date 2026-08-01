-- Add active column (needed for user revocation)
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;

-- Add 2FA columns (if not already present)
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS secret_key VARCHAR(255);
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS two_factor_enabled BOOLEAN DEFAULT FALSE;