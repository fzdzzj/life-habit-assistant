package com.fzdzzj.lifehabitassistant;

import com.fzdzzj.lifehabitassistant.pojo.AnalysisDtos;
import com.fzdzzj.lifehabitassistant.pojo.DrinkRecord;
import com.fzdzzj.lifehabitassistant.pojo.DrinkType;
import com.fzdzzj.lifehabitassistant.pojo.ExerciseIntensity;
import com.fzdzzj.lifehabitassistant.pojo.ExerciseSession;
import com.fzdzzj.lifehabitassistant.pojo.ExerciseType;
import com.fzdzzj.lifehabitassistant.pojo.HabitRecord;
import com.fzdzzj.lifehabitassistant.pojo.SleepSession;
import com.fzdzzj.lifehabitassistant.pojo.SleepType;
import com.fzdzzj.lifehabitassistant.pojo.User;
import com.fzdzzj.lifehabitassistant.server.service.AdviceGenerator;
import com.fzdzzj.lifehabitassistant.server.service.AnalysisService;
import com.fzdzzj.lifehabitassistant.server.service.HealthStatisticsService;
import com.fzdzzj.lifehabitassistant.server.service.HealthThresholds;
import com.fzdzzj.lifehabitassistant.server.service.HabitService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalysisServiceTest {
    @Test
    void trendShouldMapUnifiedDetailedStatisticsAndCountConsecutiveDays() {
        HabitService habits = mock(HabitService.class);
        AdviceGenerator advice = mock(AdviceGenerator.class);
        HealthThresholds thresholds = new HealthThresholds(420, 540, 1500, 30, 3);
        LocalDate today = LocalDate.now();
        User user = new User("demo", "hash");
        HabitRecord yesterday = record(user, today.minusDays(1), SleepType.NIGHT, today.minusDays(2).atTime(23, 0), today.minusDays(1).atTime(7, 0), ExerciseType.WALK, ExerciseIntensity.MEDIUM, 30, 1500);
        HabitRecord current = record(user, today, SleepType.NIGHT, today.minusDays(1).atTime(23, 30), today.atTime(7, 0), ExerciseType.RUN, ExerciseIntensity.HIGH, 60, 2000);
        current.addSleepSession(new SleepSession(current, SleepType.NAP, today.atTime(13, 0), today.atTime(13, 30)));
        when(habits.range(any(), any())).thenReturn(List.of(yesterday, current));

        AnalysisDtos.TrendResponse response = new AnalysisService(habits, advice,
                new HealthStatisticsService(thresholds, TestDrinkRules.defaults())).trend(7);

        assertEquals(2, response.recordCount());
        assertEquals(8.0, response.averageSleepHours());
        assertEquals(90, response.totalExerciseMinutes());
        assertEquals(2, response.consecutiveDays());
        assertEquals(0.5, response.items().getLast().napSleepHours());
        assertEquals(120, response.items().getLast().moderateEquivalentExerciseMinutes());
        assertEquals(60, response.items().getLast().exerciseMinutesByType().get(ExerciseType.RUN));
        assertTrue(response.items().stream().allMatch(AnalysisDtos.DailyTrend::achieved));
    }

    private HabitRecord record(User user, LocalDate date, SleepType sleepType, java.time.LocalDateTime start,
                               java.time.LocalDateTime end, ExerciseType exerciseType, ExerciseIntensity intensity,
                               int exerciseMinutes, int waterMl) {
        HabitRecord record = new HabitRecord(user, date, 4, null);
        record.addSleepSession(new SleepSession(record, sleepType, start, end));
        record.addExerciseSession(new ExerciseSession(record, exerciseType, null, intensity, exerciseMinutes,
                date.atTime(18, 0), null, null, null));
        record.addDrinkRecord(new DrinkRecord(record, DrinkType.WATER, null, waterMl, date.atTime(12, 0), null));
        return record;
    }
}
