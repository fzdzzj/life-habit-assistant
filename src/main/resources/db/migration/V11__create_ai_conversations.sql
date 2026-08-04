CREATE TABLE ai_conversations
(
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    user_id          BIGINT       NOT NULL,
    title            VARCHAR(100),
    created_at       DATETIME     NOT NULL,
    last_activity_at DATETIME     NOT NULL,
    PRIMARY KEY (id),
    KEY idx_ai_conversations_user_activity (user_id, last_activity_at),
    CONSTRAINT fk_ai_conversations_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE ai_conversation_messages
(
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT      NOT NULL,
    role            VARCHAR(10) NOT NULL,
    source          VARCHAR(20),
    content         TEXT        NOT NULL,
    model_name      VARCHAR(100),
    call_counted    TINYINT(1)  NOT NULL DEFAULT 0,
    created_at      DATETIME    NOT NULL,
    PRIMARY KEY (id),
    KEY idx_ai_messages_conversation_time (conversation_id, id),
    CONSTRAINT fk_ai_messages_conversation FOREIGN KEY (conversation_id) REFERENCES ai_conversations (id)
        ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
