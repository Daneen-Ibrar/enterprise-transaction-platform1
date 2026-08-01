CREATE TABLE webhook_delivery (
    id BIGSERIAL PRIMARY KEY,
    webhook_config_id BIGINT NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    url VARCHAR(500) NOT NULL,
    payload TEXT,
    status_code INT,
    response_body TEXT,
    success BOOLEAN NOT NULL,
    error_message TEXT,
    attempt_number INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);