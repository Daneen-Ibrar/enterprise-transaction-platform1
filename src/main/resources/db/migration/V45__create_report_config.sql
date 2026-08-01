CREATE TABLE report_config (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    user_id BIGINT NOT NULL,
    report_type VARCHAR(50) NOT NULL,  -- e.g., 'transaction', 'invoice'
    filters JSONB NOT NULL,            -- stores all filter parameters as JSON
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_report_config_user FOREIGN KEY (user_id) REFERENCES app_user(id)
);

CREATE INDEX idx_report_config_user_id ON report_config(user_id);