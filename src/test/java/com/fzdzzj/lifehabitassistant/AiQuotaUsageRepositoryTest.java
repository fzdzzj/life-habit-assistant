package com.fzdzzj.lifehabitassistant;

import com.fzdzzj.lifehabitassistant.pojo.AiQuotaPeriod;
import com.fzdzzj.lifehabitassistant.pojo.User;
import com.fzdzzj.lifehabitassistant.server.dao.AiQuotaUsageRepository;
import com.fzdzzj.lifehabitassistant.server.dao.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@ActiveProfiles("test")
class AiQuotaUsageRepositoryTest {
    @Autowired
    private AiQuotaUsageRepository quota;
    @Autowired
    private UserRepository users;

    @Test
    void duplicatePeriodRowShouldViolateUniqueConstraint() {
        User user = users.save(new User("quota-" + UUID.randomUUID(), "hash"));
        LocalDateTime now = LocalDateTime.now();
        String day = now.toLocalDate().toString();

        quota.saveAndFlush(new com.fzdzzj.lifehabitassistant.pojo.AiQuotaUsage(
                user, AiQuotaPeriod.DAY, day, now));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> quota.saveAndFlush(new com.fzdzzj.lifehabitassistant.pojo.AiQuotaUsage(
                        user, AiQuotaPeriod.DAY, day, now)));
    }

    @Test
    void incrementShouldStopAtConfiguredLimit() {
        User user = users.save(new User("quota-limit-" + UUID.randomUUID(), "hash"));
        LocalDateTime now = LocalDateTime.now();
        String day = now.toLocalDate().toString();
        quota.saveAndFlush(new com.fzdzzj.lifehabitassistant.pojo.AiQuotaUsage(
                user, AiQuotaPeriod.DAY, day, now));

        assertEquals(1, quota.incrementIfBelowLimit(user.getId(), AiQuotaPeriod.DAY.name(), day, 3, now));
        assertEquals(1, quota.incrementIfBelowLimit(user.getId(), AiQuotaPeriod.DAY.name(), day, 3, now));
        assertEquals(1, quota.incrementIfBelowLimit(user.getId(), AiQuotaPeriod.DAY.name(), day, 3, now));
        assertEquals(0, quota.incrementIfBelowLimit(user.getId(), AiQuotaPeriod.DAY.name(), day, 3, now));

        assertEquals(3, quota.findUsedCount(
                user.getId(), AiQuotaPeriod.DAY.name(), day).orElseThrow());
    }

    @Test
    void dayAndMonthPeriodsShouldBeCountedIndependently() {
        User user = users.save(new User("quota-period-" + UUID.randomUUID(), "hash"));
        LocalDateTime now = LocalDateTime.now();
        String day = now.toLocalDate().toString();
        String month = YearMonth.now().toString();

        quota.saveAndFlush(new com.fzdzzj.lifehabitassistant.pojo.AiQuotaUsage(
                user, AiQuotaPeriod.DAY, day, now));
        quota.saveAndFlush(new com.fzdzzj.lifehabitassistant.pojo.AiQuotaUsage(
                user, AiQuotaPeriod.MONTH, month, now));

        assertEquals(1, quota.incrementIfBelowLimit(user.getId(), AiQuotaPeriod.DAY.name(), day, 3, now));
        assertEquals(1, quota.incrementIfBelowLimit(user.getId(), AiQuotaPeriod.MONTH.name(), month, 30, now));

        assertEquals(1, quota.findUsedCount(
                user.getId(), AiQuotaPeriod.DAY.name(), day).orElseThrow());
        assertEquals(1, quota.findUsedCount(
                user.getId(), AiQuotaPeriod.MONTH.name(), month).orElseThrow());
    }
}
