CREATE TABLE drink_records
(
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    habit_record_id BIGINT      NOT NULL,
    drink_type      VARCHAR(30) NOT NULL,
    other_name      VARCHAR(50),
    volume_ml       INT         NOT NULL,
    recorded_at     DATETIME    NOT NULL,
    note            VARCHAR(500),
    PRIMARY KEY (id),
    KEY idx_drink_record_record_time (habit_record_id, recorded_at),
    CONSTRAINT fk_drink_record_habit_record FOREIGN KEY (habit_record_id) REFERENCES habit_records (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

INSERT INTO drink_records (habit_record_id, drink_type, other_name, volume_ml, recorded_at, note)
SELECT id, 'WATER', '历史汇总记录', water_ml, TIMESTAMP(record_date, '12:00:00'), '由旧版每日饮水总量迁移，具体时间未知'
FROM habit_records
WHERE water_ml > 0;

ALTER TABLE habit_records
    DROP COLUMN water_ml;
