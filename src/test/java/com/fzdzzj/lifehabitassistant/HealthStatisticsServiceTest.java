package com.fzdzzj.lifehabitassistant;

import com.fzdzzj.lifehabitassistant.pojo.*;
import com.fzdzzj.lifehabitassistant.server.service.HealthStatisticsService;
import com.fzdzzj.lifehabitassistant.server.service.HealthThresholds;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HealthStatisticsServiceTest {
    @Test
    void summarizeShouldReturnZeroMetricsForEmptyRecords() {
        LocalDate date = LocalDate.of(2026, 7, 20);
        var statistics = new HealthStatisticsService(new HealthThresholds(420, 540, 1500, 30, 3), TestDrinkRules.defaults())
                .summarize(List.of(), date);

        assertEquals(0, statistics.recordCount());
        assertEquals(0, statistics.averageSleepHours());
        assertEquals(0, statistics.consecutiveDays());
        assertEquals(0, statistics.dailyStatistics().size());
    }

    @Test
    void summarizeShouldKeepSleepSegmentsExerciseTypesAndDrinkHealthMeaning() {
        LocalDate date = LocalDate.of(2026, 7, 20);
        HabitRecord record = new HabitRecord(new User("statistics-user", "hash"), date, 4, null);
        record.addSleepSession(new SleepSession(record, SleepType.NIGHT, date.minusDays(1).atTime(23, 0), date.atTime(7, 0)));
        record.addSleepSession(new SleepSession(record, SleepType.NAP, date.atTime(13, 0), date.atTime(13, 40)));
        record.addExerciseSession(new ExerciseSession(record, ExerciseType.RUN, null, ExerciseIntensity.HIGH, 30, date.atTime(18, 0), null, null, null));
        record.addExerciseSession(new ExerciseSession(record, ExerciseType.STRENGTH, null, ExerciseIntensity.MEDIUM, 20, date.atTime(19, 0), null, null, null));
        record.addDrinkRecord(new DrinkRecord(record, DrinkType.WATER, null, 1000, date.atTime(10, 0), null));
        record.addDrinkRecord(new DrinkRecord(record, DrinkType.ENERGY_DRINK, null, 250, date.atTime(16, 0), null));

        var statistics = new HealthStatisticsService(new HealthThresholds(420, 540, 1500, 30, 3), TestDrinkRules.defaults())
                .summarize(List.of(record), date);
        var daily = statistics.dailyStatistics().getFirst();

        assertEquals(480, daily.nightSleepMinutes());
        assertEquals(40, daily.napSleepMinutes());
        assertEquals(520, daily.totalSleepMinutes());
        assertEquals(50, daily.exerciseMinutes());
        assertEquals(80, daily.moderateEquivalentExerciseMinutes());
        assertEquals(1, statistics.strengthTrainingCount());
        assertEquals(30, daily.exerciseMinutesByType().get(ExerciseType.RUN));
        assertEquals(20, daily.exerciseMinutesByType().get(ExerciseType.STRENGTH));
        assertEquals(1000, daily.hydrationMl());
        assertEquals(250, daily.riskDrinkVolumeMl());
        assertEquals(250, statistics.drinkVolumesByType().get(DrinkType.ENERGY_DRINK));
    }
}
