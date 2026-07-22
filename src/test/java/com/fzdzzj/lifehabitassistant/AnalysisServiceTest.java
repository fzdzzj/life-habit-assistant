package com.fzdzzj.lifehabitassistant;

import com.fzdzzj.lifehabitassistant.pojo.AnalysisDtos;
import com.fzdzzj.lifehabitassistant.pojo.DrinkRecord;
import com.fzdzzj.lifehabitassistant.pojo.DrinkType;
import com.fzdzzj.lifehabitassistant.pojo.HabitRecord;
import com.fzdzzj.lifehabitassistant.pojo.User;
import com.fzdzzj.lifehabitassistant.server.service.AdviceGenerator;
import com.fzdzzj.lifehabitassistant.server.service.AnalysisService;
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
    void aggregatesTrendAndCountsConsecutiveDays() {
        HabitService habits = mock(HabitService.class);
        AdviceGenerator advice = mock(AdviceGenerator.class);
        HealthThresholds thresholds = new HealthThresholds(420, 540, 1500, 30, 3);
        LocalDate today = LocalDate.now();
        User user = new User("demo", "hash");
        HabitRecord yesterday = new HabitRecord(user, today.minusDays(1), 4, null);
        yesterday.addSleepSession(new com.fzdzzj.lifehabitassistant.pojo.SleepSession(yesterday, com.fzdzzj.lifehabitassistant.pojo.SleepType.NIGHT, today.minusDays(2).atTime(23, 0), today.minusDays(1).atTime(7, 0)));
        yesterday.addExerciseSession(new com.fzdzzj.lifehabitassistant.pojo.ExerciseSession(yesterday, com.fzdzzj.lifehabitassistant.pojo.ExerciseType.WALK, null, com.fzdzzj.lifehabitassistant.pojo.ExerciseIntensity.MEDIUM, 30, today.minusDays(1).atTime(18, 0), null, null, null));
        yesterday.addDrinkRecord(new DrinkRecord(yesterday, DrinkType.WATER, null, 1500, today.minusDays(1).atTime(12, 0), null));
        HabitRecord current = new HabitRecord(user, today, 5, null);
        current.addSleepSession(new com.fzdzzj.lifehabitassistant.pojo.SleepSession(current, com.fzdzzj.lifehabitassistant.pojo.SleepType.NIGHT, today.minusDays(1).atTime(23, 30), today.atTime(7, 0)));
        current.addExerciseSession(new com.fzdzzj.lifehabitassistant.pojo.ExerciseSession(current, com.fzdzzj.lifehabitassistant.pojo.ExerciseType.RUN, null, com.fzdzzj.lifehabitassistant.pojo.ExerciseIntensity.HIGH, 60, today.atTime(18, 0), null, null, null));
        current.addDrinkRecord(new DrinkRecord(current, DrinkType.WATER, null, 2000, today.atTime(12, 0), null));
        List<HabitRecord> records = List.of(yesterday, current);
        when(habits.range(any(), any())).thenReturn(records);

        AnalysisDtos.TrendResponse response = new AnalysisService(habits, advice, thresholds, TestDrinkRules.defaults()).trend(7);

        assertEquals(2, response.recordCount());
        assertEquals(7.8, response.averageSleepHours());
        assertEquals(90, response.totalExerciseMinutes());
        assertEquals(2, response.consecutiveDays());
        assertTrue(response.items().stream().allMatch(AnalysisDtos.DailyTrend::achieved));
    }
}
