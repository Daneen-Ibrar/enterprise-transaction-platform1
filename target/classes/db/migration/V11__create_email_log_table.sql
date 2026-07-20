CREATE TABLE IF NOT EXISTS email_log (
    id BIGSERIAL PRIMARY KEY,
    recipient VARCHAR(255) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    error_message TEXT,
    sent_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_email_log_recipient ON email_log(recipient);
CREATE INDEX IF NOT EXISTS idx_email_log_status ON email_log(status);
CREATE INDEX IF NOT EXISTS idx_email_log_created_at ON email_log(created_at);