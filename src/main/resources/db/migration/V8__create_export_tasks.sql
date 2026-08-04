CREATE TABLE export_tasks
(
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    user_id       BIGINT       NOT NULL,
    report_type   VARCHAR(10)  NOT NULL,
    format        VARCHAR(10)  NOT NULL,
    period_start  DATE         NOT NULL,
    period_end    DATE         NOT NULL,
    status        VARCHAR(10)  NOT NULL,
    file_name     VARCHAR(255),
    file_content  LONGBLOB,
    error_message VARCHAR(500),
    created_at    DATETIME     NOT NULL,
    started_at    DATETIME,
    finished_at   DATETIME,
    PRIMARY KEY (id),
    KEY idx_export_task_user_created (user_id, created_at),
    CONSTRAINT fk_export_task_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
