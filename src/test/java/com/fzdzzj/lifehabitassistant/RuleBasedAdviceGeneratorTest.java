package com.fzdzzj.lifehabitassistant;

import com.fzdzzj.lifehabitassistant.pojo.ExerciseIntensity;
import com.fzdzzj.lifehabitassistant.pojo.ExerciseSession;
import com.fzdzzj.lifehabitassistant.pojo.ExerciseType;
import com.fzdzzj.lifehabitassistant.pojo.DailyGoals;
import com.fzdzzj.lifehabitassistant.pojo.HabitRecord;
import com.fzdzzj.lifehabitassistant.pojo.SleepSession;
import com.fzdzzj.lifehabitassistant.pojo.SleepType;
import com.fzdzzj.lifehabitassistant.pojo.User;
import com.fzdzzj.lifehabitassistant.server.service.HealthStatisticsService;
import com.fzdzzj.lifehabitassistant.server.service.RuleBasedAdviceGenerator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleBasedAdviceGeneratorTest {
    private static final DailyGoals DEFAULT_GOALS = new DailyGoals(420, 540, 1500, 30, 3);

    @Test
    void shouldWarnWhenWeeklyExerciseAndStrengthTargetsAreNotMet() {
        LocalDate today = LocalDate.now();
        List<HabitRecord> records = IntStream.range(0, 7).mapToObj(offset -> healthyRecord(today.minusDays(offset))).toList();

        var statistics = new HealthStatisticsService(TestDrinkRules.defaults()).summarize(records, today, DEFAULT_GOALS);
        var response = new RuleBasedAdviceGenerator(TestDrinkRules.defaults()).generate(7, statistics, DEFAULT_GOALS);

        assertTrue(response.risks().contains("中等强度运动当量未达到每周 150 分钟"));
        assertTrue(response.risks().contains("力量训练频次未达到每周 2 次"));
    }

    @Test
    void customGoalsShouldDriveSleepHydrationAndExerciseRiskMessages() {
        LocalDate today = LocalDate.now();
        List<HabitRecord> records = List.of(healthyRecord(today));

        var statistics = new HealthStatisticsService(TestDrinkRules.defaults()).summarize(records, today, DEFAULT_GOALS);
        var strict = new RuleBasedAdviceGenerator(TestDrinkRules.defaults())
                .generate(1, statistics, new DailyGoals(540, 600, 2000, 60, 4));

        assertTrue(strict.risks().contains("平均睡眠不足 9 小时"));
        assertTrue(strict.risks().contains("日均运动不足"));
        assertTrue(strict.suggestions().stream().anyMatch(s -> s.contains("2000 ml 有效补水")));
    }

    @Test
    void relaxedGoalsShouldRemoveDefaultRisks() {
        LocalDate today = LocalDate.now();
        List<HabitRecord> records = List.of(healthyRecord(today));

        var statistics = new HealthStatisticsService(TestDrinkRules.defaults()).summarize(records, today, DEFAULT_GOALS);
        var relaxed = new RuleBasedAdviceGenerator(TestDrinkRules.defaults())
                .generate(1, statistics, new DailyGoals(300, 600, 1000, 0, 2));

        assertFalse(relaxed.risks().contains("平均睡眠不足 7 小时"));
        assertFalse(relaxed.risks().contains("日均运动不足"));
    }

    private HabitRecord healthyRecord(LocalDate date) {
        HabitRecord record = new HabitRecord(new User("demo-" + date, "hash"), date, 4, null);
        record.addSleepSession(new SleepSession(record, SleepType.NIGHT, date.minusDays(1).atTime(23, 0), date.atTime(7, 0)));
        return record;
    }
}
