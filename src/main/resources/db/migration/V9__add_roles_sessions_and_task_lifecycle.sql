ALTER TABLE users
    ADD COLUMN role VARCHAR(10) NOT NULL DEFAULT 'USER';

CREATE TABLE sessions
(
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    user_id        BIGINT       NOT NULL,
    device_name    VARCHAR(100),
    device_id      VARCHAR(100),
    ip_address     VARCHAR(45),
    user_agent     VARCHAR(255),
    created_at     DATETIME     NOT NULL,
    last_active_at DATETIME     NOT NULL,
    revoked_at     DATETIME,
    PRIMARY KEY (id),
    KEY idx_sessions_user (user_id),
    CONSTRAINT fk_sessions_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE refresh_tokens
(
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    session_id BIGINT      NOT NULL,
    token_hash CHAR(64)    NOT NULL,
    expires_at DATETIME    NOT NULL,
    revoked_at DATETIME,
    created_at DATETIME    NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_token_hash (token_hash),
    KEY idx_refresh_token_session (session_id),
    CONSTRAINT fk_refresh_token_session FOREIGN KEY (session_id) REFERENCES sessions (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE password_reset_tokens
(
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    token_hash CHAR(64)    NOT NULL,
    expires_at DATETIME    NOT NULL,
    used_at    DATETIME,
    created_at DATETIME    NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_password_reset_token_hash (token_hash),
    KEY idx_password_reset_user (user_id),
    CONSTRAINT fk_password_reset_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

ALTER TABLE export_tasks
    ADD COLUMN cancelled_at DATETIME;
