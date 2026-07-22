CREATE TABLE sleep_sessions
(
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    habit_record_id BIGINT      NOT NULL,
    sleep_type      VARCHAR(20) NOT NULL,
    sleep_start_at  DATETIME    NOT NULL,
    wake_at         DATETIME    NOT NULL,
    PRIMARY KEY (id),
    KEY idx_sleep_session_record_start (habit_record_id, sleep_start_at),
    CONSTRAINT fk_sleep_session_record FOREIGN KEY (habit_record_id) REFERENCES habit_records (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

INSERT INTO sleep_sessions (habit_record_id, sleep_type, sleep_start_at, wake_at)
SELECT id,
       'NIGHT',
       CASE
           WHEN bedtime >= wake_time THEN TIMESTAMP(DATE_SUB(record_date, INTERVAL 1 DAY), bedtime)
           ELSE TIMESTAMP(record_date, bedtime)
           END,
       TIMESTAMP(record_date, wake_time)
FROM habit_records;

ALTER TABLE habit_records
    DROP COLUMN bedtime,
    DROP COLUMN wake_time;
