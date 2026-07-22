package com.fzdzzj.lifehabitassistant;

import com.fzdzzj.lifehabitassistant.pojo.HabitRecord;
import com.fzdzzj.lifehabitassistant.pojo.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HabitRecordTest {
    @Test
    void calculatesSleepAcrossMidnight() {
        LocalDate date = LocalDate.now();
        HabitRecord r = new HabitRecord(new User("demo", "hash"), date, 3, null);
        r.addSleepSession(new com.fzdzzj.lifehabitassistant.pojo.SleepSession(r, com.fzdzzj.lifehabitassistant.pojo.SleepType.NIGHT, date.minusDays(1).atTime(23, 30), date.atTime(7, 0)));
        assertEquals(450, r.sleepMinutes());
    }

    @Test
    void aggregatesNightSleepAndNap() {
        LocalDate date = LocalDate.now();
        HabitRecord r = new HabitRecord(new User("demo", "hash"), date, 3, null);
        r.addSleepSession(new com.fzdzzj.lifehabitassistant.pojo.SleepSession(r, com.fzdzzj.lifehabitassistant.pojo.SleepType.NIGHT, date.minusDays(1).atTime(23, 0), date.atTime(7, 0)));
        r.addSleepSession(new com.fzdzzj.lifehabitassistant.pojo.SleepSession(r, com.fzdzzj.lifehabitassistant.pojo.SleepType.NAP, date.atTime(13, 0), date.atTime(13, 30)));

        assertEquals(510, r.sleepMinutes());
    }

}
