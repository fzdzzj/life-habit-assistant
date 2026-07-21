package com.fzdzzj.lifehabitassistant.server.service;

import com.fzdzzj.lifehabitassistant.pojo.HabitRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Centralizes the configurable thresholds used by trend and advice features. */
@Component
public class HealthThresholds {
    private final int minimumSleepMinutes;
    private final int maximumSleepMinutes;
    private final int minimumWaterMl;
    private final int minimumExerciseMinutes;
    private final int minimumDietScore;

    public HealthThresholds(
            @Value("${app.health.minimum-sleep-minutes}") int minimumSleepMinutes,
            @Value("${app.health.maximum-sleep-minutes}") int maximumSleepMinutes,
            @Value("${app.health.minimum-water-ml}") int minimumWaterMl,
            @Value("${app.health.minimum-exercise-minutes}") int minimumExerciseMinutes,
            @Value("${app.health.minimum-diet-score}") int minimumDietScore) {
        this.minimumSleepMinutes = minimumSleepMinutes;
        this.maximumSleepMinutes = maximumSleepMinutes;
        this.minimumWaterMl = minimumWaterMl;
        this.minimumExerciseMinutes = minimumExerciseMinutes;
        this.minimumDietScore = minimumDietScore;
    }

    public boolean isAchieved(HabitRecord record) {
        return record.sleepMinutes() >= minimumSleepMinutes
                && record.sleepMinutes() <= maximumSleepMinutes
                && record.getDietScore() >= minimumDietScore
                && record.getExerciseMinutes() >= minimumExerciseMinutes
                && record.getWaterMl() >= minimumWaterMl;
    }

    public int minimumSleepMinutes() {
        return minimumSleepMinutes;
    }

    public int maximumSleepMinutes() {
        return maximumSleepMinutes;
    }

    public int minimumWaterMl() {
        return minimumWaterMl;
    }

    public int minimumExerciseMinutes() {
        return minimumExerciseMinutes;
    }

    public int minimumDietScore() {
        return minimumDietScore;
    }
}
