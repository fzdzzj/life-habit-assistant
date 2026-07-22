CREATE TABLE exercise_sessions
(
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    habit_record_id  BIGINT      NOT NULL,
    exercise_type    VARCHAR(30) NOT NULL,
    other_name       VARCHAR(50),
    intensity        VARCHAR(10) NOT NULL,
    duration_minutes INT         NOT NULL,
    started_at       DATETIME    NOT NULL,
    distance_km      DECIMAL(6, 2),
    calories_kcal    INT,
    note             VARCHAR(500),
    PRIMARY KEY (id),
    KEY idx_exercise_session_record_start (habit_record_id, started_at),
    CONSTRAINT fk_exercise_session_record FOREIGN KEY (habit_record_id) REFERENCES habit_records (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

INSERT INTO exercise_sessions (habit_record_id, exercise_type, other_name, intensity, duration_minutes, started_at, note)
SELECT id, 'OTHER', '历史汇总记录', 'MEDIUM', exercise_minutes, TIMESTAMP(record_date, '12:00:00'), '由旧版每日运动总分钟数迁移，具体项目和强度未知'
FROM habit_records
WHERE exercise_minutes > 0;

ALTER TABLE habit_records
    DROP COLUMN exercise_minutes;
