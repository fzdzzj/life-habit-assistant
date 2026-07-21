package com.fzdzzj.lifehabitassistant;

import com.fzdzzj.lifehabitassistant.pojo.HabitRecord;
import com.fzdzzj.lifehabitassistant.pojo.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HabitRecordTest {
    @Test
    void calculatesSleepAcrossMidnight() {
        HabitRecord r = new HabitRecord(new User("demo", "hash"), LocalDate.now(), LocalTime.of(23, 30), LocalTime.of(7, 0), 3, 30, 1500, null);
        assertEquals(450, r.sleepMinutes());
    }
}
