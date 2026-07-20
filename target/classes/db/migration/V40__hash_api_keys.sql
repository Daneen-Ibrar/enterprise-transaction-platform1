-- ============================================================
-- V40: Hash API Keys
-- Adds hash-based authentication for API keys
-- ============================================================

-- Add hash column for API keys (we keep key_value for the display prefix)
ALTER TABLE api_key ADD COLUMN IF NOT EXISTS key_hash VARCHAR(255);
ALTER TABLE api_key ADD COLUMN IF NOT EXISTS key_prefix VARCHAR(20);
ALTER TABLE api_key ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP;

-- For existing keys, generate hash from existing key_value
UPDATE api_key SET 
    key_hash = key_value,
    key_prefix = SUBSTRING(key_value, 1, 8)
WHERE key_hash IS NULL;

-- Make key_hash NOT NULL after migration
ALTER TABLE api_key ALTER COLUMN key_hash SET NOT NULL;

-- Create indexes
CREATE INDEX IF NOT EXISTS idx_api_key_hash ON api_key(key_hash);
CREATE INDEX IF NOT EXISTS idx_api_key_prefix ON api_key(key_prefix);

-- Note: last_used_at already exists from an earlier migration, so we don't add it again