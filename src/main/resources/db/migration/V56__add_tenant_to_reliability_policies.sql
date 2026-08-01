-- V56__add_tenant_to_reliability_policies.sql
-- Add tenant_id to retry_policy and circuit_breaker_policy

-- 1. Add tenant_id columns (if they don't exist)
ALTER TABLE retry_policy ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE circuit_breaker_policy ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1;

-- 2. Drop the old unique constraints (which are named with _key suffix)
--    PostgreSQL requires dropping the constraint, not the index.
ALTER TABLE retry_policy DROP CONSTRAINT IF EXISTS retry_policy_operation_type_key;
ALTER TABLE circuit_breaker_policy DROP CONSTRAINT IF EXISTS circuit_breaker_policy_operation_type_key;

-- 3. Add new unique constraints on (tenant_id, operation_type)
--    These will automatically create backing indexes.
ALTER TABLE retry_policy ADD CONSTRAINT uq_retry_policy_tenant_operation UNIQUE (tenant_id, operation_type);
ALTER TABLE circuit_breaker_policy ADD CONSTRAINT uq_circuit_breaker_policy_tenant_operation UNIQUE (tenant_id, operation_type);

-- 4. Drop any leftover indexes (they are redundant now)
DROP INDEX IF EXISTS idx_retry_policy_tenant_operation;
DROP INDEX IF EXISTS idx_circuit_breaker_policy_tenant_operation;