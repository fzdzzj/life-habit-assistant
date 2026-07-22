package com.fzdzzj.lifehabitassistant.config;

import com.fzdzzj.lifehabitassistant.pojo.HabitRecord;
import com.fzdzzj.lifehabitassistant.pojo.SleepSession;
import com.fzdzzj.lifehabitassistant.pojo.SleepType;
import com.fzdzzj.lifehabitassistant.pojo.User;
import com.fzdzzj.lifehabitassistant.server.dao.HabitRecordRepository;
import com.fzdzzj.lifehabitassistant.server.dao.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;

@Configuration
@Profile("demo")
public class DemoDataInitializer {
    @Bean
    CommandLineRunner seedDemoData(UserRepository users, HabitRecordRepository records, PasswordEncoder encoder) {
        return args -> {
            User demo = users.findByUsername("demo")
                    .orElseGet(() -> users.save(new User("demo", encoder.encode("demo123456"))));
            LocalDate today = LocalDate.now();
            for (int offset = 34; offset >= 0; offset--) {
                LocalDate date = today.minusDays(offset);
                if (records.findByUserAndRecordDate(demo, date).isEmpty()) {
                    int variation = offset % 4;
                    HabitRecord record = new HabitRecord(demo, date, 3 + variation % 3, 30 + variation * 10, 1500 + variation * 150, "demo record");
                    record.addSleepSession(new SleepSession(record, SleepType.NIGHT, date.minusDays(1).atTime(23, variation * 10), date.atTime(7, 0)));
                    records.save(record);
                }
            }
        };
    }
}
