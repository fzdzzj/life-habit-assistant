package com.fzdzzj.lifehabitassistant.server.service;

import com.fzdzzj.lifehabitassistant.config.ReportCache;
import com.fzdzzj.lifehabitassistant.pojo.AiAdviceDtos;
import com.fzdzzj.lifehabitassistant.pojo.AiAdviceType;
import com.fzdzzj.lifehabitassistant.pojo.AnalysisDtos;
import com.fzdzzj.lifehabitassistant.pojo.DailyGoals;
import com.fzdzzj.lifehabitassistant.pojo.HabitRecord;
import com.fzdzzj.lifehabitassistant.pojo.HealthStatistics;
import com.fzdzzj.lifehabitassistant.pojo.ReportDtos;
import com.fzdzzj.lifehabitassistant.pojo.User;
import com.fzdzzj.lifehabitassistant.server.dao.AiAdviceHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class ReportService {
    private final HabitService habits;
    private final HealthStatisticsService statistics;
    private final AdviceGenerator advice;
    private final AiAdviceHistoryRepository history;
    private final AiAdviceContentParser contentParser;
    private final CurrentUser currentUser;
    private final GoalService goals;
    private final ReportCache cache;

    public ReportService(HabitService habits, HealthStatisticsService statistics, AdviceGenerator advice,
                         AiAdviceHistoryRepository history, AiAdviceContentParser contentParser,
                         CurrentUser currentUser, GoalService goals, ReportCache cache) {
        this.habits = habits;
        this.statistics = statistics;
        this.advice = advice;
        this.history = history;
        this.contentParser = contentParser;
        this.currentUser = currentUser;
        this.goals = goals;
        this.cache = cache;
    }

    @Transactional(readOnly = true)
    public ReportDtos.ReportResponse weekly(LocalDate anyDay) {
        return weekly(currentUser.require(), anyDay);
    }

    /**
     * Cache-aware weekly report for an already-resolved user. Used by the async
     * exporter, which runs without a SecurityContext.
     */
    @Transactional(readOnly = true)
    public ReportDtos.ReportResponse weekly(User user, LocalDate anyDay) {
        LocalDate start = anyDay.with(DayOfWeek.MONDAY);
        return cached(user, "weekly", start, start.plusDays(6));
    }

    @Transactional(readOnly = true)
    public ReportDtos.ReportResponse monthly(YearMonth month) {
        return monthly(currentUser.require(), month);
    }

    /** Cache-aware monthly report for an already-resolved user (async exporter path). */
    @Transactional(readOnly = true)
    public ReportDtos.ReportResponse monthly(User user, YearMonth month) {
        return cached(user, "monthly", month.atDay(1), month.atEndOfMonth());
    }

    /** Cache-aware arbitrary-range report for the async exporter. */
    @Transactional(readOnly = true)
    public ReportDtos.ReportResponse customForUser(User user, LocalDate start, LocalDate end) {
        return cached(user, "custom", start, end);
    }

    private ReportDtos.ReportResponse cached(User user, String type, LocalDate start, LocalDate end) {
        return cache.get(user.getId(), type, start, end).orElseGet(() -> {
            ReportDtos.ReportResponse report = build(type, start, end, user);
            cache.put(user.getId(), type, start, end, report);
            return report;
        });
    }

    private ReportDtos.ReportResponse build(String type, LocalDate start, LocalDate end, User user) {
        List<HabitRecord> records = habits.range(user, start, end);
        DailyGoals effectiveGoals = goals.effective(user);
        HealthStatistics summary = statistics.summarize(records, end, effectiveGoals);
        int days = Math.toIntExact(end.toEpochDay() - start.toEpochDay() + 1L);
        var analysis = advice.generate(days, summary, effectiveGoals);
        return new ReportDtos.ReportResponse(type, start, end, summary.recordCount(), summary.averageSleepHours(),
                summary.averageDietScore(), summary.totalExerciseMinutes(), summary.averageHydrationMl(),
                summary.totalRiskDrinkVolumeMl(), achievementRate(summary), effectiveGoals,
                summary.dailyStatistics().stream().map(this::daily).toList(), weekly(records, effectiveGoals),
                analysis.risks(), analysis.suggestions(), latestSavedAdvice(type, start, end, user));
    }

    private AiAdviceDtos.AdviceSnapshot latestSavedAdvice(String type, LocalDate start, LocalDate end, User user) {
        AiAdviceType adviceType = "weekly".equals(type) ? AiAdviceType.WEEKLY_REPORT : AiAdviceType.MONTHLY_REPORT;
        return history.findFirstByUserIdAndAdviceTypeAndPeriodStartAndPeriodEndOrderByCreatedAtDesc(
                        user.getId(), adviceType, start, end)
                .map(saved -> new AiAdviceDtos.AdviceSnapshot(saved.getId(), saved.getSource(),
                        contentParser.parse(saved.getContent()), saved.getCreatedAt()))
                .orElse(null);
    }

    private List<ReportDtos.WeekSummary> weekly(List<HabitRecord> records, DailyGoals goals) {
        return records.stream().collect(Collectors.groupingBy(record -> record.getRecordDate().with(DayOfWeek.MONDAY),
                        TreeMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(entry -> weekSummary(entry.getKey(), entry.getValue(), goals))
                .toList();
    }

    private ReportDtos.WeekSummary weekSummary(LocalDate weekStart, List<HabitRecord> records, DailyGoals goals) {
        HealthStatistics summary = statistics.summarize(records, weekStart.plusDays(6), goals);
        return new ReportDtos.WeekSummary(weekStart, summary.averageSleepHours(), summary.totalExerciseMinutes(),
                summary.averageHydrationMl(), summary.totalRiskDrinkVolumeMl());
    }

    private AnalysisDtos.DailyTrend daily(HealthStatistics.DailyStatistics daily) {
        return new AnalysisDtos.DailyTrend(daily.date(), round(daily.totalSleepMinutes() / 60d), daily.dietScore(),
                daily.exerciseMinutes(), daily.hydrationMl(), daily.riskDrinkVolumeMl(), daily.achieved(),
                round(daily.nightSleepMinutes() / 60d), round(daily.napSleepMinutes() / 60d),
                daily.moderateEquivalentExerciseMinutes(), daily.exerciseMinutesByType());
    }

    private double achievementRate(HealthStatistics statistics) {
        return round(statistics.dailyStatistics().stream().filter(HealthStatistics.DailyStatistics::achieved).count()
                * 100d / Math.max(1, statistics.recordCount()));
    }

    private double round(double value) {
        return Math.round(value * 10d) / 10d;
    }
}
