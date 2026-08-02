CREATE TABLE ai_advice_history
(
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    user_id        BIGINT       NOT NULL,
    advice_type    VARCHAR(20)  NOT NULL,
    period_start   DATE         NOT NULL,
    period_end     DATE         NOT NULL,
    source         VARCHAR(20)  NOT NULL,
    model_name     VARCHAR(100),
    prompt_version VARCHAR(50)  NOT NULL,
    content        TEXT         NOT NULL,
    call_counted   BOOLEAN      NOT NULL,
    created_at     DATETIME     NOT NULL,
    PRIMARY KEY (id),
    KEY idx_ai_advice_user_period (user_id, advice_type, period_start, period_end, created_at),
    CONSTRAINT fk_ai_advice_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
