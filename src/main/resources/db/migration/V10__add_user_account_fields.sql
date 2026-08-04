ALTER TABLE users
    ADD COLUMN email VARCHAR(255) NULL,
    ADD COLUMN enabled TINYINT(1) NOT NULL DEFAULT 1,
    ADD COLUMN ai_daily_limit INT NULL,
    ADD COLUMN ai_monthly_limit INT NULL;

CREATE UNIQUE INDEX uk_users_email ON users (email);
