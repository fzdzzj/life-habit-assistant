CREATE TABLE daily_goals
(
    id                       BIGINT   NOT NULL AUTO_INCREMENT,
    user_id                  BIGINT   NOT NULL,
    minimum_sleep_minutes    INT      NOT NULL,
    maximum_sleep_minutes    INT      NOT NULL,
    minimum_hydration_ml     INT      NOT NULL,
    minimum_exercise_minutes INT      NOT NULL,
    minimum_diet_score       INT      NOT NULL,
    updated_at               DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_daily_goal_user (user_id),
    CONSTRAINT fk_daily_goal_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
