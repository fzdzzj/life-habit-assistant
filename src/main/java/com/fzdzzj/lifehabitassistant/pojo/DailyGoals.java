package com.fzdzzj.lifehabitassistant.pojo;

/**
 * Effective daily health goals for one user: either the saved custom goals
 * or the global defaults. Immutable value used by statistics, rule advice,
 * reports and the AI prompt.
 */
public record DailyGoals(int minimumSleepMinutes, int maximumSleepMinutes, int minimumHydrationMl,
                         int minimumExerciseMinutes, int minimumDietScore) {
    public DailyGoals {
        if (minimumSleepMinutes <= 0 || maximumSleepMinutes < minimumSleepMinutes
                || minimumHydrationMl < 0 || minimumExerciseMinutes < 0
                || minimumDietScore < 1 || minimumDietScore > 5) {
            throw new IllegalArgumentException("每日目标取值不合法");
        }
    }

    public boolean isAchieved(long sleepMinutes, int dietScore, int moderateEquivalentExerciseMinutes, int hydrationMl) {
        return sleepMinutes >= minimumSleepMinutes && sleepMinutes <= maximumSleepMinutes
                && dietScore >= minimumDietScore
                && moderateEquivalentExerciseMinutes >= minimumExerciseMinutes
                && hydrationMl >= minimumHydrationMl;
    }
}
