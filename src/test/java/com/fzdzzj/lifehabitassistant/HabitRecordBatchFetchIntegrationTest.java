package com.fzdzzj.lifehabitassistant;

import com.fzdzzj.lifehabitassistant.pojo.DrinkRecord;
import com.fzdzzj.lifehabitassistant.pojo.DrinkType;
import com.fzdzzj.lifehabitassistant.pojo.ExerciseIntensity;
import com.fzdzzj.lifehabitassistant.pojo.ExerciseSession;
import com.fzdzzj.lifehabitassistant.pojo.ExerciseType;
import com.fzdzzj.lifehabitassistant.pojo.HabitRecord;
import com.fzdzzj.lifehabitassistant.pojo.SleepSession;
import com.fzdzzj.lifehabitassistant.pojo.SleepType;
import com.fzdzzj.lifehabitassistant.pojo.User;
import com.fzdzzj.lifehabitassistant.server.dao.DrinkRecordRepository;
import com.fzdzzj.lifehabitassistant.server.dao.ExerciseSessionRepository;
import com.fzdzzj.lifehabitassistant.server.dao.HabitRecordRepository;
import com.fzdzzj.lifehabitassistant.server.dao.SleepSessionRepository;
import com.fzdzzj.lifehabitassistant.server.dao.UserRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class HabitRecordBatchFetchIntegrationTest {
    @Autowired
    private UserRepository users;
    @Autowired
    private HabitRecordRepository records;
    @Autowired
    private SleepSessionRepository sleepSessions;
    @Autowired
    private ExerciseSessionRepository exerciseSessions;
    @Autowired
    private DrinkRecordRepository drinkRecords;
    @Autowired
    private EntityManager entityManager;

    @Test
    void rangeShouldBatchLoadAllDetailCollections() {
        LocalDate firstDay = LocalDate.of(2026, 7, 1);
        User user = users.save(new User("batch-fetch-user", "hash"));
        for (int offset = 0; offset < 3; offset++) {
            LocalDate day = firstDay.plusDays(offset);
            HabitRecord record = records.save(new HabitRecord(user, day, 4, null));
            sleepSessions.save(new SleepSession(record, SleepType.NIGHT, day.minusDays(1).atTime(23, 0), day.atTime(7, 0)));
            exerciseSessions.save(new ExerciseSession(record, ExerciseType.RUN, null, ExerciseIntensity.MEDIUM, 30, day.atTime(18, 0), null, null, null));
            drinkRecords.save(new DrinkRecord(record, DrinkType.WATER, null, 500, day.atTime(10, 0), null));
        }
        entityManager.flush();
        entityManager.clear();

        Statistics statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        try {
            statistics.clear();
            List<HabitRecord> loadedRecords = records.findByUserAndRecordDateBetweenOrderByRecordDateAsc(user, firstDay, firstDay.plusDays(2));

            long totalDetails = loadedRecords.stream()
                    .mapToLong(record -> record.getSleepSessions().size() + record.getExerciseSessions().size() + record.getDrinkRecords().size())
                    .sum();

            assertEquals(9, totalDetails);
            assertTrue(statistics.getPrepareStatementCount() <= 4,
                    () -> "expected one record query plus one query per detail collection, but got " + statistics.getPrepareStatementCount());
        } finally {
            statistics.clear();
            statistics.setStatisticsEnabled(false);
        }
    }
}
