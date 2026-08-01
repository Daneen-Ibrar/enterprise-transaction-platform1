CREATE TABLE IF NOT EXISTS invoice (
    id BIGSERIAL PRIMARY KEY,
    amount DECIMAL(19,2) NOT NULL,
    description TEXT NOT NULL,
    customer_email VARCHAR(255) NOT NULL,
    merchant_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    requires_approval BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);