-- ============================================================
-- V51__add_version_to_rule_tables.sql
-- Description: Add optimistic locking version fields to rule tables
-- This prevents concurrent updates from overwriting each other
-- ============================================================

-- Add version column to approval_rule
ALTER TABLE approval_rule ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- Add version column to refund_rule
ALTER TABLE refund_rule ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- Add version column to suspicion_rule
ALTER TABLE suspicion_rule ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;