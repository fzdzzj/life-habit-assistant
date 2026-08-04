package com.fzdzzj.lifehabitassistant.server.service;

import com.fzdzzj.lifehabitassistant.pojo.AnalysisDtos;
import com.fzdzzj.lifehabitassistant.pojo.DailyGoals;
import com.fzdzzj.lifehabitassistant.pojo.HealthStatistics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class AnalysisService {
    private final HabitService habits;
    private final AdviceGenerator advice;
    private final HealthStatisticsService statistics;
    private final GoalService goals;

    public AnalysisService(HabitService habits, AdviceGenerator advice, HealthStatisticsService statistics,
                           GoalService goals) {
        this.habits = habits;
        this.advice = advice;
        this.statistics = statistics;
        this.goals = goals;
    }

    @Transactional(readOnly = true)
    public AnalysisDtos.TrendResponse trend(int days) {
        LocalDate today = LocalDate.now();
        HealthStatistics summary = statistics.summarize(records(days, today), today, goals.get());
        return new AnalysisDtos.TrendResponse(days, summary.recordCount(), summary.averageSleepHours(),
                summary.averageDietScore(), summary.totalExerciseMinutes(), summary.averageHydrationMl(),
                summary.consecutiveDays(), summary.dailyStatistics().stream().map(this::daily).toList());
    }

    @Transactional(readOnly = true)
    public AnalysisDtos.AnalysisResponse analysis(int days) {
        LocalDate today = LocalDate.now();
        return advice.generate(days, statistics.summarize(records(days, today), today, goals.get()), goals.get());
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
