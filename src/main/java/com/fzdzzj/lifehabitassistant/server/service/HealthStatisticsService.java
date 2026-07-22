package com.fzdzzj.lifehabitassistant.server.service;

import com.fzdzzj.lifehabitassistant.pojo.DrinkRecord;
import com.fzdzzj.lifehabitassistant.pojo.DrinkType;
import com.fzdzzj.lifehabitassistant.pojo.ExerciseSession;
import com.fzdzzj.lifehabitassistant.pojo.ExerciseType;
import com.fzdzzj.lifehabitassistant.pojo.HabitRecord;
import com.fzdzzj.lifehabitassistant.pojo.HealthStatistics;
import com.fzdzzj.lifehabitassistant.pojo.SleepSession;
import com.fzdzzj.lifehabitassistant.pojo.SleepType;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The single calculation boundary for trends, advice and reports.
 * It deliberately receives loaded aggregate roots so user scoping and database access stay in HabitService.
 */
@Service
public class HealthStatisticsService {
    private final HealthThresholds thresholds;
    private final DrinkHealthRules drinkRules;

    public HealthStatisticsService(HealthThresholds thresholds, DrinkHealthRules drinkRules) {
        this.thresholds = thresholds;
        this.drinkRules = drinkRules;
    }

    public HealthStatistics summarize(List<HabitRecord> records, LocalDate consecutiveAnchor) {
        List<HealthStatistics.DailyStatistics> dailyStatistics = records.stream()
                .sorted(java.util.Comparator.comparing(HabitRecord::getRecordDate))
                .map(this::daily)
                .toList();
        return new HealthStatistics(
                records.size(),
                round(dailyStatistics.stream().mapToLong(HealthStatistics.DailyStatistics::totalSleepMinutes).average().orElse(0) / 60d),
                round(dailyStatistics.stream().mapToInt(HealthStatistics.DailyStatistics::dietScore).average().orElse(0)),
                dailyStatistics.stream().mapToInt(HealthStatistics.DailyStatistics::exerciseMinutes).sum(),
                dailyStatistics.stream().mapToInt(HealthStatistics.DailyStatistics::moderateEquivalentExerciseMinutes).sum(),
                records.stream().mapToLong(HabitRecord::strengthExerciseCount).mapToInt(Math::toIntExact).sum(),
                round(dailyStatistics.stream().mapToInt(HealthStatistics.DailyStatistics::hydrationMl).average().orElse(0)),
                dailyStatistics.stream().mapToInt(HealthStatistics.DailyStatistics::riskDrinkVolumeMl).sum(),
                consecutiveDays(records, consecutiveAnchor),
                dailyStatistics,
                exerciseMinutesByType(records),
                drinkVolumesByType(records));
    }

    private HealthStatistics.DailyStatistics daily(HabitRecord record) {
        long nightSleepMinutes = sleepMinutes(record, SleepType.NIGHT);
        long napSleepMinutes = sleepMinutes(record, SleepType.NAP);
        int exerciseMinutes = record.exerciseMinutes();
        int moderateEquivalentMinutes = record.moderateEquivalentExerciseMinutes();
        int hydrationMl = drinkRules.hydrationMl(record);
        return new HealthStatistics.DailyStatistics(
                record.getRecordDate(), nightSleepMinutes, napSleepMinutes, nightSleepMinutes + napSleepMinutes,
                record.getDietScore(), exerciseMinutes, moderateEquivalentMinutes, exerciseMinutesByType(record),
                hydrationMl, drinkRules.riskDrinkVolumeMl(record),
                thresholds.isAchieved(nightSleepMinutes + napSleepMinutes, record.getDietScore(), moderateEquivalentMinutes, hydrationMl));
    }

    private long sleepMinutes(HabitRecord record, SleepType sleepType) {
        return record.getSleepSessions().stream()
                .filter(session -> session.getSleepType() == sleepType)
                .mapToLong(this::durationMinutes)
                .sum();
    }

    private long durationMinutes(SleepSession session) {
        return java.time.Duration.between(session.getSleepStartAt(), session.getWakeAt()).toMinutes();
    }

    private Map<ExerciseType, Integer> exerciseMinutesByType(HabitRecord record) {
        Map<ExerciseType, Integer> totals = new EnumMap<>(ExerciseType.class);
        for (ExerciseSession session : record.getExerciseSessions()) {
            totals.merge(session.getExerciseType(), session.getDurationMinutes(), Integer::sum);
        }
        return totals;
    }

    private Map<ExerciseType, Integer> exerciseMinutesByType(List<HabitRecord> records) {
        Map<ExerciseType, Integer> totals = new EnumMap<>(ExerciseType.class);
        for (HabitRecord record : records) {
            exerciseMinutesByType(record).forEach((type, minutes) -> totals.merge(type, minutes, Integer::sum));
        }
        return totals;
    }

    private Map<DrinkType, Integer> drinkVolumesByType(List<HabitRecord> records) {
        Map<DrinkType, Integer> totals = new EnumMap<>(DrinkType.class);
        for (HabitRecord record : records) {
            for (DrinkRecord drink : record.getDrinkRecords()) {
                totals.merge(drink.getDrinkType(), drink.getVolumeMl(), Integer::sum);
            }
        }
        return totals;
    }

    private int consecutiveDays(List<HabitRecord> records, LocalDate anchor) {
        Set<LocalDate> dates = new HashSet<>();
        records.forEach(record -> dates.add(record.getRecordDate()));
        int consecutiveDays = 0;
        for (LocalDate date = anchor; dates.contains(date); date = date.minusDays(1)) {
            consecutiveDays++;
        }
        return consecutiveDays;
    }

    private double round(double value) {
        return Math.round(value * 10d) / 10d;
    }
}
