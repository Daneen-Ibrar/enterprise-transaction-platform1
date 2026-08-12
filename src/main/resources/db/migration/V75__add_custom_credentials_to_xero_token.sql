-- V75__add_custom_credentials_to_xero_token.sql
-- ============================================================
-- Add custom client_id and client_secret to xero_token table
-- ============================================================

ALTER TABLE xero_token ADD COLUMN IF NOT EXISTS client_id VARCHAR(255);
ALTER TABLE xero_token ADD COLUMN IF NOT EXISTS client_secret VARCHAR(255);

-- For existing tokens, use the default credentials from environment
UPDATE xero_token SET client_id = 'BAED4F8CB038420A99830D1A383BA8FD' WHERE client_id IS NULL;
UPDATE xero_token SET client_secret = '4UMZvj9q6z3AKA2Mw-aEedlLX-spbAsZnps5KEjY8OTOrhsW' WHERE client_secret IS NULL;

ALTER TABLE xero_token ALTER COLUMN client_id SET NOT NULL;
ALTER TABLE xero_token ALTER COLUMN client_secret SET NOT NULL;