-- ============================================================
-- V25: Fix Suspicion Rules – Correct SpEL Syntax
-- This migration is repeatable; it ensures the rules are always
-- present with the correct expressions.
-- ============================================================

-- 1. Delete any duplicate or incorrectly formatted rules
--    (We keep only the ones we're about to insert/update)
DELETE FROM suspicion_rule
WHERE id NOT IN (1, 2, 3, 4)
   OR condition_expression NOT LIKE '#%';

-- 2. Insert or update the rules with correct SpEL syntax
--    (Using ON CONFLICT to handle existing rows)

INSERT INTO suspicion_rule (id, rule_priority, condition_expression, risk_level, description, active, created_at)
VALUES
    (1, 10, '#description.matches("(?i).*urgent.*")', 'YELLOW', 'Contains "urgent" (case-insensitive)', true, CURRENT_TIMESTAMP),
    (2, 20, '#description.matches("(?i).*fraud.*")', 'RED', 'Contains "fraud" (case-insensitive)', true, CURRENT_TIMESTAMP),
    (3, 30, '#description.matches("(?i).*scam.*")', 'RED', 'Contains "scam" (case-insensitive)', true, CURRENT_TIMESTAMP),
    (4, 40, '#customerEmail.matches("(?i).*test.*")', 'YELLOW', 'Test email domain (case-insensitive)', true, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO UPDATE SET
    rule_priority = EXCLUDED.rule_priority,
    condition_expression = EXCLUDED.condition_expression,
    risk_level = EXCLUDED.risk_level,
    description = EXCLUDED.description,
    active = EXCLUDED.active,
    created_at = EXCLUDED.created_at;

-- 3. Ensure no other rules exist (cleanup)
DELETE FROM suspicion_rule WHERE id NOT IN (1, 2, 3, 4);