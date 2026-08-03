CREATE TABLE ai_quota_usage
(
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    period_type VARCHAR(10)  NOT NULL,
    period_key  VARCHAR(10)  NOT NULL,
    used_count  INT          NOT NULL DEFAULT 0,
    updated_at  DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_quota_user_period (user_id, period_type, period_key),
    CONSTRAINT fk_ai_quota_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
