package com.fzdzzj.lifehabitassistant.server.service;

import com.fzdzzj.lifehabitassistant.pojo.AnalysisDtos;
import com.fzdzzj.lifehabitassistant.pojo.HabitRecord;
import com.fzdzzj.lifehabitassistant.pojo.ReportDtos;
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
    private final AnalysisService analysis;
    private final AdviceGenerator advice;
    private final HealthThresholds thresholds;
    private final DrinkHealthRules drinkRules;

    public ReportService(AnalysisService analysis, AdviceGenerator advice, HealthThresholds thresholds, DrinkHealthRules drinkRules) {
        this.analysis = analysis;
        this.advice = advice;
        this.thresholds = thresholds;
        this.drinkRules = drinkRules;
    }

    @Transactional(readOnly = true)
    public ReportDtos.ReportResponse weekly(LocalDate anyDay) {
        LocalDate start = anyDay.with(DayOfWeek.MONDAY), end = start.plusDays(6);
        return build("weekly", start, end);
    }

    @Transactional(readOnly = true)
    public ReportDtos.ReportResponse monthly(YearMonth month) {
        return build("monthly", month.atDay(1), month.atEndOfMonth());
    }

    private ReportDtos.ReportResponse build(String type, LocalDate start, LocalDate end) {
        List<HabitRecord> r = analysis.between(start, end);
        List<AnalysisDtos.DailyTrend> days = r.stream().map(this::daily).toList();
        AnalysisDtos.AnalysisResponse a = advice.generate((int) (end.toEpochDay() - start.toEpochDay() + 1), r);
        return new ReportDtos.ReportResponse(type, start, end, r.size(), round(days.stream().mapToDouble(AnalysisDtos.DailyTrend::sleepHours).average().orElse(0)), round(days.stream().mapToInt(AnalysisDtos.DailyTrend::dietScore).average().orElse(0)), days.stream().mapToInt(AnalysisDtos.DailyTrend::exerciseMinutes).sum(), round(days.stream().mapToInt(AnalysisDtos.DailyTrend::hydrationMl).average().orElse(0)), days.stream().mapToInt(AnalysisDtos.DailyTrend::riskDrinkVolumeMl).sum(), round(days.stream().filter(AnalysisDtos.DailyTrend::achieved).count() * 100d / Math.max(1, days.size())), days, weekly(r), a.risks(), a.suggestions());
    }

    private List<ReportDtos.WeekSummary> weekly(List<HabitRecord> records) {
        return records.stream().collect(Collectors.groupingBy(r -> r.getRecordDate().with(DayOfWeek.MONDAY), TreeMap::new, Collectors.toList())).entrySet().stream().map(e -> {
            var r = e.getValue();
            return new ReportDtos.WeekSummary(e.getKey(), round(r.stream().mapToLong(HabitRecord::sleepMinutes).average().orElse(0) / 60d), r.stream().mapToInt(HabitRecord::exerciseMinutes).sum(), round(r.stream().mapToInt(drinkRules::hydrationMl).average().orElse(0)), r.stream().mapToInt(drinkRules::riskDrinkVolumeMl).sum());
        }).toList();
    }

    private AnalysisDtos.DailyTrend daily(HabitRecord record) {
        int hydrationMl = drinkRules.hydrationMl(record);
        return new AnalysisDtos.DailyTrend(record.getRecordDate(), round(record.sleepMinutes() / 60d),
                record.getDietScore(), record.exerciseMinutes(), hydrationMl,
                drinkRules.riskDrinkVolumeMl(record), thresholds.isAchieved(record, hydrationMl));
    }

    private boolean achieved(HabitRecord r) {
        return thresholds.isAchieved(r, drinkRules.hydrationMl(r));
    }

    private double round(double n) {
        return Math.round(n * 10) / 10d;
    }
}
