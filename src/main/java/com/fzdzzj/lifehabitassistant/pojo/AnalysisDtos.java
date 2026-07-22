package com.fzdzzj.lifehabitassistant.pojo;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class AnalysisDtos {
    private AnalysisDtos() {
    }

    public record DailyTrend(LocalDate date, double sleepHours, int dietScore, int exerciseMinutes, int hydrationMl, int riskDrinkVolumeMl,
                             boolean achieved, double nightSleepHours, double napSleepHours,
                             int moderateEquivalentExerciseMinutes, Map<ExerciseType, Integer> exerciseMinutesByType) {
        public DailyTrend {
            exerciseMinutesByType = Map.copyOf(exerciseMinutesByType);
        }

        public DailyTrend(LocalDate date, double sleepHours, int dietScore, int exerciseMinutes, int hydrationMl,
                          int riskDrinkVolumeMl, boolean achieved) {
            this(date, sleepHours, dietScore, exerciseMinutes, hydrationMl, riskDrinkVolumeMl, achieved,
                    sleepHours, 0, exerciseMinutes, Map.of());
        }
    }

    public record TrendResponse(int days, int recordCount, double averageSleepHours, double averageDietScore,
                                int totalExerciseMinutes, double averageHydrationMl, int consecutiveDays,
                                List<DailyTrend> items) {
    }

    public record AnalysisResponse(int days, int recordCount, String summary, List<String> risks,
                                   List<String> suggestions) {
    }
}
