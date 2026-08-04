package com.fzdzzj.lifehabitassistant.server.service;

import com.fzdzzj.lifehabitassistant.pojo.AiQuotaPeriod;
import com.fzdzzj.lifehabitassistant.pojo.AiQuotaUsage;
import com.fzdzzj.lifehabitassistant.pojo.User;
import com.fzdzzj.lifehabitassistant.server.dao.AiQuotaUsageRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.YearMonth;

/**
 * Shared AI quota authority for report interpretation and multi-turn
 * conversation: both features atomically draw from the same daily/monthly
 * bucket, and per-user overrides from the admin quota APIs apply to both.
 */
@Service
public class AiQuotaService {
    private final AiQuotaUsageRepository quotaRepository;
    private final AiAdviceProperties properties;

    public AiQuotaService(AiQuotaUsageRepository quotaRepository, AiAdviceProperties properties) {
        this.quotaRepository = quotaRepository;
        this.properties = properties;
    }

    /**
     * Atomically reserve quota for one day and one month inside the current
     * transaction. Either both succeed or the transaction rolls back both
     * increments.
     */
    public void occupy(User user) {
        LocalDateTime now = LocalDateTime.now();
        String dayKey = now.toLocalDate().toString();
        String monthKey = YearMonth.now().toString();
        ensureRow(user, AiQuotaPeriod.DAY, dayKey, now);
        if (quotaRepository.incrementIfBelowLimit(user.getId(), AiQuotaPeriod.DAY.name(), dayKey,
                dailyLimitOf(user), now) != 1) {
            throw new QuotaExceededException("daily quota exhausted");
        }
        ensureRow(user, AiQuotaPeriod.MONTH, monthKey, now);
        if (quotaRepository.incrementIfBelowLimit(user.getId(), AiQuotaPeriod.MONTH.name(), monthKey,
                monthlyLimitOf(user), now) != 1) {
            throw new QuotaExceededException("monthly quota exhausted");
        }
    }

    public QuotaSnapshot usage(User user) {
        LocalDateTime now = LocalDateTime.now();
        int dailyUsed = quotaRepository.findUsedCount(
                        user.getId(), AiQuotaPeriod.DAY.name(), now.toLocalDate().toString())
                .orElse(0);
        int monthlyUsed = quotaRepository.findUsedCount(
                        user.getId(), AiQuotaPeriod.MONTH.name(), YearMonth.now().toString())
                .orElse(0);
        return new QuotaSnapshot(dailyUsed, dailyLimitOf(user), monthlyUsed, monthlyLimitOf(user));
    }

    public int dailyLimitOf(User user) {
        return user.getAiDailyLimit() == null ? properties.dailyLimit() : user.getAiDailyLimit();
    }

    public int monthlyLimitOf(User user) {
        return user.getAiMonthlyLimit() == null ? properties.monthlyLimit() : user.getAiMonthlyLimit();
    }

    private void ensureRow(User user, AiQuotaPeriod period, String key, LocalDateTime now) {
        if (quotaRepository.findByUserIdAndPeriodTypeAndPeriodKey(user.getId(), period, key).isPresent()) {
            return;
        }
        try {
            quotaRepository.saveAndFlush(new AiQuotaUsage(user, period, key, now));
        } catch (DataIntegrityViolationException ignored) {
            // 并发请求已创建同一行：继续执行，由下面的原子 UPDATE 负责扣减
        }
    }

    public record QuotaSnapshot(int dailyUsed, int dailyLimit, int monthlyUsed, int monthlyLimit) {
    }

    public static final class QuotaExceededException extends RuntimeException {
        QuotaExceededException(String message) {
            super(message);
        }
    }
}
