package com.fzdzzj.lifehabitassistant.pojo;

import java.time.LocalDate;
import java.util.List;

public final class ReportDtos {
    private ReportDtos() {
    }

    public record WeekSummary(LocalDate weekStart, double averageSleepHours, int exerciseMinutes,
                              double averageHydrationMl, int riskDrinkVolumeMl) {
    }

    public record ReportResponse(String type, LocalDate periodStart, LocalDate periodEnd, int recordCount,
                                 double averageSleepHours, double averageDietScore, int totalExerciseMinutes,
                                 double averageHydrationMl, int totalRiskDrinkVolumeMl, double achievementRate,
                                 List<AnalysisDtos.DailyTrend> dailyTrends, List<WeekSummary> weeklySummaries,
                                 List<String> risks, List<String> suggestions) {
    }
}
