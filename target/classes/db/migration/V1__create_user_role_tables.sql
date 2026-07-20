CREATE TABLE IF NOT EXISTS role (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS app_user (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS user_role (
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    role_id BIGINT NOT NULL REFERENCES role(id),
    PRIMARY KEY (user_id, role_id)
);

INSERT INTO role (name, description) VALUES
    ('CUSTOMER', 'Can view and pay own invoices'),
    ('MERCHANT', 'Can create invoices and view received payments'),
    ('ADMIN', 'Can approve invoices, issue refunds, manage workflows'),
    ('AUDITOR', 'Read-only access to audit logs and verification')
ON CONFLICT (name) DO NOTHING;