package com.fzdzzj.lifehabitassistant.pojo;

import java.time.LocalDate;
import java.util.List;

public final class AnalysisDtos {
    private AnalysisDtos() {
    }

    public record DailyTrend(LocalDate date, double sleepHours, int dietScore, int exerciseMinutes, int hydrationMl, int riskDrinkVolumeMl,
                             boolean achieved) {
    }

    public record TrendResponse(int days, int recordCount, double averageSleepHours, double averageDietScore,
                                int totalExerciseMinutes, double averageHydrationMl, int consecutiveDays,
                                List<DailyTrend> items) {
    }

    public record AnalysisResponse(int days, int recordCount, String summary, List<String> risks,
                                   List<String> suggestions) {
    }
}
