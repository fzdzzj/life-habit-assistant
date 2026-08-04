package com.fzdzzj.lifehabitassistant.server.service;

import com.fzdzzj.lifehabitassistant.pojo.DailyGoals;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Centralizes the configurable global default thresholds. Custom per-user goals
 * (daily_goals) override these; every consumer reads effective goals instead of
 * reaching into this component directly.
 */
@Component
public class HealthThresholds {
    private final int minimumSleepMinutes;
    private final int maximumSleepMinutes;
    private final int minimumHydrationMl;
    private final int minimumExerciseMinutes;
    private final int minimumDietScore;

    public HealthThresholds(
            @Value("${app.health.minimum-sleep-minutes}") int minimumSleepMinutes,
            @Value("${app.health.maximum-sleep-minutes}") int maximumSleepMinutes,
            @Value("${app.health.minimum-hydration-ml}") int minimumHydrationMl,
            @Value("${app.health.minimum-exercise-minutes}") int minimumExerciseMinutes,
            @Value("${app.health.minimum-diet-score}") int minimumDietScore) {
        this.minimumSleepMinutes = minimumSleepMinutes;
        this.maximumSleepMinutes = maximumSleepMinutes;
        this.minimumHydrationMl = minimumHydrationMl;
        this.minimumExerciseMinutes = minimumExerciseMinutes;
        this.minimumDietScore = minimumDietScore;
    }

    public DailyGoals toGoals() {
        return new DailyGoals(minimumSleepMinutes, maximumSleepMinutes, minimumHydrationMl,
                minimumExerciseMinutes, minimumDietScore);
    }
}
