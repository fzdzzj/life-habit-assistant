package com.fzdzzj.lifehabitassistant.server.service;

import com.fzdzzj.lifehabitassistant.pojo.AnalysisDtos;
import com.fzdzzj.lifehabitassistant.pojo.HealthStatistics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class AnalysisService {
    private final HabitService habits;
    private final AdviceGenerator advice;
    private final HealthStatisticsService statistics;

    public AnalysisService(HabitService habits, AdviceGenerator advice, HealthStatisticsService statistics) {
        this.habits = habits;
        this.advice = advice;
        this.statistics = statistics;
    }

    @Transactional(readOnly = true)
    public AnalysisDtos.TrendResponse trend(int days) {
        LocalDate today = LocalDate.now();
        HealthStatistics summary = statistics.summarize(records(days, today), today);
        return new AnalysisDtos.TrendResponse(days, summary.recordCount(), summary.averageSleepHours(),
                summary.averageDietScore(), summary.totalExerciseMinutes(), summary.averageHydrationMl(),
                summary.consecutiveDays(), summary.dailyStatistics().stream().map(this::daily).toList());
    }

    @Transactional(readOnly = true)
    public AnalysisDtos.AnalysisResponse analysis(int days) {
        LocalDate today = LocalDate.now();
        return advice.generate(days, statistics.summarize(records(days, today), today));
    }

    private java.util.List<com.fzdzzj.lifehabitassistant.pojo.HabitRecord> records(int days, LocalDate end) {
        if (days < 1 || days > 366) {
            throw new IllegalArgumentException("days 必须在 1 到 366 之间");
        }
        return habits.range(end.minusDays(days - 1L), end);
    }

    private AnalysisDtos.DailyTrend daily(HealthStatistics.DailyStatistics daily) {
        return new AnalysisDtos.DailyTrend(daily.date(), round(daily.totalSleepMinutes() / 60d), daily.dietScore(),
                daily.exerciseMinutes(), daily.hydrationMl(), daily.riskDrinkVolumeMl(), daily.achieved(),
                round(daily.nightSleepMinutes() / 60d), round(daily.napSleepMinutes() / 60d),
                daily.moderateEquivalentExerciseMinutes(), daily.exerciseMinutesByType());
    }

    private double round(double value) {
        return Math.round(value * 10d) / 10d;
    }
}
