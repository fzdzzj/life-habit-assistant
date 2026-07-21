package com.fzdzzj.lifehabitassistant;

import com.fzdzzj.lifehabitassistant.pojo.AnalysisDtos;
import com.fzdzzj.lifehabitassistant.pojo.HabitRecord;
import com.fzdzzj.lifehabitassistant.pojo.User;
import com.fzdzzj.lifehabitassistant.server.service.AdviceGenerator;
import com.fzdzzj.lifehabitassistant.server.service.AnalysisService;
import com.fzdzzj.lifehabitassistant.server.service.HealthThresholds;
import com.fzdzzj.lifehabitassistant.server.service.HabitService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
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
        List<HabitRecord> records = List.of(
                new HabitRecord(user, today.minusDays(1), LocalTime.of(23, 0), LocalTime.of(7, 0), 4, 30, 1500, null),
                new HabitRecord(user, today, LocalTime.of(23, 30), LocalTime.of(7, 0), 5, 60, 2000, null));
        when(habits.range(any(), any())).thenReturn(records);

        AnalysisDtos.TrendResponse response = new AnalysisService(habits, advice, thresholds).trend(7);

        assertEquals(2, response.recordCount());
        assertEquals(7.8, response.averageSleepHours());
        assertEquals(90, response.totalExerciseMinutes());
        assertEquals(2, response.consecutiveDays());
        assertTrue(response.items().stream().allMatch(AnalysisDtos.DailyTrend::achieved));
    }
}
