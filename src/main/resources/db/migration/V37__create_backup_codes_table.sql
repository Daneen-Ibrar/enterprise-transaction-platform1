-- Backup codes for 2FA recovery
CREATE TABLE IF NOT EXISTS backup_code (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    code_hash VARCHAR(255) NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_backup_code_user_id ON backup_code(user_id);
CREATE INDEX idx_backup_code_used ON backup_code(used);