package com.fzdzzj.lifehabitassistant.pojo;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Immutable, period-level health metrics derived from detailed daily records. */
public record HealthStatistics(
        int recordCount,
        double averageSleepHours,
        double averageDietScore,
        int totalExerciseMinutes,
        int totalModerateEquivalentExerciseMinutes,
        int strengthTrainingCount,
        double averageHydrationMl,
        int totalRiskDrinkVolumeMl,
        int consecutiveDays,
        List<DailyStatistics> dailyStatistics,
        Map<ExerciseType, Integer> exerciseMinutesByType,
        Map<DrinkType, Integer> drinkVolumesByType) {

    public HealthStatistics {
        dailyStatistics = List.copyOf(dailyStatistics);
        exerciseMinutesByType = Map.copyOf(exerciseMinutesByType);
        drinkVolumesByType = Map.copyOf(drinkVolumesByType);
    }

    public record DailyStatistics(
            LocalDate date,
            long nightSleepMinutes,
            long napSleepMinutes,
            long totalSleepMinutes,
            int dietScore,
            int exerciseMinutes,
            int moderateEquivalentExerciseMinutes,
            Map<ExerciseType, Integer> exerciseMinutesByType,
            int hydrationMl,
            int riskDrinkVolumeMl,
            boolean achieved) {
        public DailyStatistics {
            exerciseMinutesByType = Map.copyOf(exerciseMinutesByType);
        }
    }
}
