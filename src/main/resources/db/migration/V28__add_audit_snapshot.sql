-- Extend audit_event table with snapshot columns
ALTER TABLE audit_event ADD COLUMN IF NOT EXISTS entity_type VARCHAR(50);
ALTER TABLE audit_event ADD COLUMN IF NOT EXISTS entity_id BIGINT;
ALTER TABLE audit_event ADD COLUMN IF NOT EXISTS previous_state JSONB;
ALTER TABLE audit_event ADD COLUMN IF NOT EXISTS current_state JSONB;

-- Index for faster lookups
CREATE INDEX IF NOT EXISTS idx_audit_entity ON audit_event(entity_type, entity_id);