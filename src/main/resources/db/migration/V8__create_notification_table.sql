CREATE TABLE IF NOT EXISTS notification (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    type VARCHAR(30) NOT NULL,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    link VARCHAR(255),
    read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_notification_user_id ON notification(user_id);
CREATE INDEX idx_notification_read ON notification(read);
CREATE INDEX idx_notification_created_at ON notification(created_at DESC);