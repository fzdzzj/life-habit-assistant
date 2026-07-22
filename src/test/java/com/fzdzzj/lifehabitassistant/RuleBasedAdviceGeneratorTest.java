package com.fzdzzj.lifehabitassistant;

import com.fzdzzj.lifehabitassistant.pojo.ExerciseIntensity;
import com.fzdzzj.lifehabitassistant.pojo.ExerciseSession;
import com.fzdzzj.lifehabitassistant.pojo.ExerciseType;
import com.fzdzzj.lifehabitassistant.pojo.HabitRecord;
import com.fzdzzj.lifehabitassistant.pojo.SleepSession;
import com.fzdzzj.lifehabitassistant.pojo.SleepType;
import com.fzdzzj.lifehabitassistant.pojo.User;
import com.fzdzzj.lifehabitassistant.server.service.HealthThresholds;
import com.fzdzzj.lifehabitassistant.server.service.RuleBasedAdviceGenerator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleBasedAdviceGeneratorTest {
    @Test
    void shouldWarnWhenWeeklyExerciseAndStrengthTargetsAreNotMet() {
        LocalDate today = LocalDate.now();
        List<HabitRecord> records = IntStream.range(0, 7).mapToObj(offset -> healthyRecord(today.minusDays(offset))).toList();

        var response = new RuleBasedAdviceGenerator(new HealthThresholds(420, 540, 1500, 30, 3)).generate(7, records);

        assertTrue(response.risks().contains("中等强度运动当量未达到每周 150 分钟"));
        assertTrue(response.risks().contains("力量训练频次未达到每周 2 次"));
    }

    private HabitRecord healthyRecord(LocalDate date) {
        HabitRecord record = new HabitRecord(new User("demo-" + date, "hash"), date, 4, 1800, null);
        record.addSleepSession(new SleepSession(record, SleepType.NIGHT, date.minusDays(1).atTime(23, 0), date.atTime(7, 0)));
        return record;
    }
}
