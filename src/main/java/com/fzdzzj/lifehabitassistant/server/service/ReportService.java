package com.fzdzzj.lifehabitassistant.server.service;

import com.fzdzzj.lifehabitassistant.pojo.AnalysisDtos;
import com.fzdzzj.lifehabitassistant.pojo.HabitRecord;
import com.fzdzzj.lifehabitassistant.pojo.HealthStatistics;
import com.fzdzzj.lifehabitassistant.pojo.ReportDtos;
import com.fzdzzj.lifehabitassistant.pojo.AiAdviceDtos;
import com.fzdzzj.lifehabitassistant.pojo.AiAdviceType;
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

    public ReportService(HabitService habits, HealthStatisticsService statistics, AdviceGenerator advice,
                         AiAdviceHistoryRepository history, AiAdviceContentParser contentParser,
                         CurrentUser currentUser) {
        this.habits = habits;
        this.statistics = statistics;
        this.advice = advice;
        this.history = history;
        this.contentParser = contentParser;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public ReportDtos.ReportResponse weekly(LocalDate anyDay) {
        LocalDate start = anyDay.with(DayOfWeek.MONDAY);
        return build("weekly", start, start.plusDays(6));
    }

    @Transactional(readOnly = true)
    public ReportDtos.ReportResponse monthly(YearMonth month) {
        return build("monthly", month.atDay(1), month.atEndOfMonth());
    }

    private ReportDtos.ReportResponse build(String type, LocalDate start, LocalDate end) {
        List<HabitRecord> records = habits.range(start, end);
        HealthStatistics summary = statistics.summarize(records, end);
        int days = Math.toIntExact(end.toEpochDay() - start.toEpochDay() + 1L);
        var analysis = advice.generate(days, summary);
        return new ReportDtos.ReportResponse(type, start, end, summary.recordCount(), summary.averageSleepHours(),
                summary.averageDietScore(), summary.totalExerciseMinutes(), summary.averageHydrationMl(),
                summary.totalRiskDrinkVolumeMl(), achievementRate(summary),
                summary.dailyStatistics().stream().map(this::daily).toList(), weekly(records),
                analysis.risks(), analysis.suggestions(), latestSavedAdvice(type, start, end));
    }

    private AiAdviceDtos.AdviceSnapshot latestSavedAdvice(String type, LocalDate start, LocalDate end) {
        AiAdviceType adviceType = "weekly".equals(type) ? AiAdviceType.WEEKLY_REPORT : AiAdviceType.MONTHLY_REPORT;
        return history.findFirstByUserIdAndAdviceTypeAndPeriodStartAndPeriodEndOrderByCreatedAtDesc(
                        currentUser.require().getId(), adviceType, start, end)
                .map(saved -> new AiAdviceDtos.AdviceSnapshot(saved.getId(), saved.getSource(),
                        contentParser.parse(saved.getContent()), saved.getCreatedAt()))
                .orElse(null);
    }

    private List<ReportDtos.WeekSummary> weekly(List<HabitRecord> records) {
        return records.stream().collect(Collectors.groupingBy(record -> record.getRecordDate().with(DayOfWeek.MONDAY),
                        TreeMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(entry -> weekSummary(entry.getKey(), entry.getValue()))
                .toList();
    }

    private ReportDtos.WeekSummary weekSummary(LocalDate weekStart, List<HabitRecord> records) {
        HealthStatistics summary = statistics.summarize(records, weekStart.plusDays(6));
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
