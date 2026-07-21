package com.fzdzzj.lifehabitassistant.config;

import com.fzdzzj.lifehabitassistant.pojo.HabitRecord;
import com.fzdzzj.lifehabitassistant.pojo.User;
import com.fzdzzj.lifehabitassistant.server.dao.HabitRecordRepository;
import com.fzdzzj.lifehabitassistant.server.dao.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.time.LocalTime;

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
                    records.save(new HabitRecord(demo, date, LocalTime.of(23, variation * 10), LocalTime.of(7, 0), 3 + variation % 3, 30 + variation * 10, 1500 + variation * 150, "demo record"));
                }
            }
        };
    }
}
