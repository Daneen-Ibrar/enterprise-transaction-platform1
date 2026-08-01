-- Change JSONB columns to TEXT to avoid casting errors
ALTER TABLE audit_event ALTER COLUMN previous_state TYPE TEXT;
ALTER TABLE audit_event ALTER COLUMN current_state TYPE TEXT;