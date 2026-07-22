package com.fzdzzj.lifehabitassistant.server.service;

import com.fzdzzj.lifehabitassistant.pojo.AnalysisDtos;
import com.fzdzzj.lifehabitassistant.pojo.HabitRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class AnalysisService {
    private final HabitService habits;
    private final AdviceGenerator advice;
    private final HealthThresholds thresholds;

    public AnalysisService(HabitService habits, AdviceGenerator advice, HealthThresholds thresholds) {
        this.habits = habits;
        this.advice = advice;
        this.thresholds = thresholds;
    }

    @Transactional(readOnly = true)
    public AnalysisDtos.TrendResponse trend(int days) {
        List<HabitRecord> records = records(days);
        List<AnalysisDtos.DailyTrend> items = records.stream().map(this::daily).toList();
        return new AnalysisDtos.TrendResponse(days, records.size(), round(records.stream().mapToLong(HabitRecord::sleepMinutes).average().orElse(0) / 60d), round(records.stream().mapToInt(HabitRecord::getDietScore).average().orElse(0)), records.stream().mapToInt(HabitRecord::exerciseMinutes).sum(), round(records.stream().mapToInt(HabitRecord::getWaterMl).average().orElse(0)), consecutive(records), items);
    }

    @Transactional(readOnly = true)
    public AnalysisDtos.AnalysisResponse analysis(int days) {
        return advice.generate(days, records(days));
    }

    @Transactional(readOnly = true)
    public List<HabitRecord> between(LocalDate start, LocalDate end) {
        return habits.range(start, end);
    }

    private List<HabitRecord> records(int days) {
        if (days < 1 || days > 366) throw new IllegalArgumentException("days 必须在 1 到 366 之间");
        LocalDate end = LocalDate.now();
        return habits.range(end.minusDays(days - 1), end);
    }

    private AnalysisDtos.DailyTrend daily(HabitRecord r) {
        return new AnalysisDtos.DailyTrend(r.getRecordDate(), round(r.sleepMinutes() / 60d), r.getDietScore(), r.exerciseMinutes(), r.getWaterMl(), thresholds.isAchieved(r));
    }

    private int consecutive(List<HabitRecord> rs) {
        if (rs.isEmpty()) return 0;
        Set<LocalDate> dates = new HashSet<>();
        rs.forEach(r -> dates.add(r.getRecordDate()));
        int c = 0;
        for (LocalDate d = LocalDate.now(); dates.contains(d); d = d.minusDays(1)) c++;
        return c;
    }

    private double round(double v) {
        return Math.round(v * 10d) / 10d;
    }
}
