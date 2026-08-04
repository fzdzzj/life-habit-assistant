package com.fzdzzj.lifehabitassistant.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fzdzzj.lifehabitassistant.pojo.*;
import com.fzdzzj.lifehabitassistant.config.ReportCache;
import com.fzdzzj.lifehabitassistant.server.dao.AiAdviceHistoryRepository;
import com.fzdzzj.lifehabitassistant.server.dao.AiQuotaUsageRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Explicit, user-triggered AI advice with deterministic fallback.
 *
 * Facts stay in HealthStatistics and rule advice stays authoritative; the model only
 * rephrases sanitized aggregates. Every explicit request is persisted to history so
 * exports can attach the latest saved interpretation without invoking AI again.
 */
@Service
public class AiAdviceService {
    private static final Logger log = LoggerFactory.getLogger(AiAdviceService.class);
    private static final String DISCLAIMER = "本建议仅作健康生活方式参考，不构成医疗诊断或治疗建议；如有健康问题请咨询医生。";

    private final HabitService habits;
    private final HealthStatisticsService statistics;
    private final RuleBasedAdviceGenerator ruleAdvice;
    private final AiAdviceHistoryRepository historyRepository;
    private final AiQuotaUsageRepository quotaRepository;
    private final AiAdviceProperties properties;
    private final AiSystemPromptLoader promptLoader;
    private final OpenAiChatClient chatClient;
    private final AiAdviceContentParser contentParser;
    private final ObjectMapper objectMapper;
    private final CurrentUser currentUser;
    private final GoalService goals;
    private final ReportCache reportCache;

    public AiAdviceService(HabitService habits, HealthStatisticsService statistics,
                           RuleBasedAdviceGenerator ruleAdvice, AiAdviceHistoryRepository historyRepository,
                           AiQuotaUsageRepository quotaRepository, AiAdviceProperties properties,
                           AiSystemPromptLoader promptLoader, OpenAiChatClient chatClient,
                           AiAdviceContentParser contentParser, ObjectMapper objectMapper, CurrentUser currentUser,
                           GoalService goals, ReportCache reportCache) {
        this.habits = habits;
        this.statistics = statistics;
        this.ruleAdvice = ruleAdvice;
        this.historyRepository = historyRepository;
        this.quotaRepository = quotaRepository;
        this.properties = properties;
        this.promptLoader = promptLoader;
        this.chatClient = chatClient;
        this.contentParser = contentParser;
        this.objectMapper = objectMapper;
        this.currentUser = currentUser;
        this.goals = goals;
        this.reportCache = reportCache;
    }

    @Transactional
    public AiAdviceDtos.AiAdviceResponse analysis(int days) {
        if (days < 1 || days > 366) {
            throw new IllegalArgumentException("days 必须在 1 到 366 之间");
        }
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(days - 1L);
        var effectiveGoals = goals.get();
        return generate(currentUser.require(), AiAdviceType.ANALYSIS, start, end, days,
                statistics.summarize(habits.range(start, end), end, effectiveGoals), effectiveGoals);
    }

    @Transactional
    public AiAdviceDtos.AiAdviceResponse weekly(LocalDate anyDay) {
        LocalDate start = anyDay.with(DayOfWeek.MONDAY);
        LocalDate end = start.plusDays(6);
        var effectiveGoals = goals.get();
        return generate(currentUser.require(), AiAdviceType.WEEKLY_REPORT, start, end, 7,
                statistics.summarize(habits.range(start, end), end, effectiveGoals), effectiveGoals);
    }

    @Transactional
    public AiAdviceDtos.AiAdviceResponse monthly(YearMonth month) {
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();
        int days = Math.toIntExact(end.toEpochDay() - start.toEpochDay() + 1L);
        var effectiveGoals = goals.get();
        return generate(currentUser.require(), AiAdviceType.MONTHLY_REPORT, start, end, days,
                statistics.summarize(habits.range(start, end), end, effectiveGoals), effectiveGoals);
    }

    private AiAdviceDtos.AiAdviceResponse generate(User user, AiAdviceType type, LocalDate start, LocalDate end,
                                                   int days, HealthStatistics summary, DailyGoals goals) {
        AnalysisDtos.AnalysisResponse rule = ruleAdvice.generate(days, summary, goals);
        if (!eligible(properties, summary)) {
            return persistAndRespond(user, type, start, end, AdviceSource.RULE_FALLBACK, toContent(rule),
                    null, false);
        }
        try {
            occupyQuota(user);
        } catch (QuotaExceededException ex) {
            return persistAndRespond(user, type, start, end, AdviceSource.RULE_FALLBACK, toContent(rule),
                    null, false);
        }
        try {
            String raw = chatClient.chat(promptLoader.load(), userPrompt(days, summary, rule, goals));
            AiAdviceDtos.AiAdviceContent content = contentParser.parse(raw);
            return persistAndRespond(user, type, start, end, AdviceSource.AI, content,
                    properties.model(), true);
        } catch (RuntimeException ex) {
            log.warn("OpenAI advice failed for user {}: {}", user.getId(), ex.toString());
            return persistAndRespond(user, type, start, end, AdviceSource.RULE_FALLBACK, toContent(rule),
                    properties.model(), true);
        }
    }

    private AiAdviceDtos.AiAdviceResponse persistAndRespond(User user, AiAdviceType type, LocalDate start,
                                                            LocalDate end, AdviceSource source,
                                                            AiAdviceDtos.AiAdviceContent content, String modelName,
                                                            boolean callCounted) {
        AiAdviceHistory saved = historyRepository.save(new AiAdviceHistory(user, type, start, end, source, modelName,
                properties.promptVersion(), toJson(content), callCounted));
        reportCache.evictUser(user.getId());
        Quota quota = usage(user);
        return new AiAdviceDtos.AiAdviceResponse(source, content, saved.getId(), saved.getCreatedAt(),
                quota.dailyUsed, dailyLimitOf(user), quota.monthlyUsed, monthlyLimitOf(user));
    }

    private boolean eligible(AiAdviceProperties properties, HealthStatistics summary) {
        return summary.recordCount() > 0 && properties.enabled()
                && notBlank(properties.apiKey()) && notBlank(properties.model());
    }

    /**
     * Atomically reserve quota for one day and one month inside the current transaction.
     * Either both succeed or the transaction rolls back both increments.
     */
    private void occupyQuota(User user) {
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

    private int dailyLimitOf(User user) {
        return user.getAiDailyLimit() == null ? properties.dailyLimit() : user.getAiDailyLimit();
    }

    private int monthlyLimitOf(User user) {
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

    private Quota usage(User user) {
        LocalDateTime now = LocalDateTime.now();
        int dailyUsed = quotaRepository.findUsedCount(
                        user.getId(), AiQuotaPeriod.DAY.name(), now.toLocalDate().toString())
                .orElse(0);
        int monthlyUsed = quotaRepository.findUsedCount(
                        user.getId(), AiQuotaPeriod.MONTH.name(), YearMonth.now().toString())
                .orElse(0);
        return new Quota(dailyUsed, monthlyUsed);
    }

    private String userPrompt(int days, HealthStatistics summary, AnalysisDtos.AnalysisResponse rule,
                              DailyGoals goals) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("days", days);
        payload.put("recordCount", summary.recordCount());
        payload.put("averageSleepHours", summary.averageSleepHours());
        payload.put("averageDietScore", summary.averageDietScore());
        payload.put("totalExerciseMinutes", summary.totalExerciseMinutes());
        payload.put("averageHydrationMl", summary.averageHydrationMl());
        payload.put("totalRiskDrinkVolumeMl", summary.totalRiskDrinkVolumeMl());
        payload.put("consecutiveDays", summary.consecutiveDays());
        payload.put("exerciseMinutesByType", summary.exerciseMinutesByType());
        payload.put("drinkVolumesByType", summary.drinkVolumesByType());
        payload.put("ruleRisks", rule.risks());
        payload.put("ruleSuggestions", rule.suggestions());
        payload.put("dailyGoals", goals);
        return "以下是最近 " + days + " 天的脱敏健康聚合指标：\n" + toJson(payload);
    }

    private AiAdviceDtos.AiAdviceContent toContent(AnalysisDtos.AnalysisResponse rule) {
        return new AiAdviceDtos.AiAdviceContent(
                rule.summary(),
                rule.risks().isEmpty() ? "暂无明显风险" : String.join("；", rule.risks()),
                rule.suggestions(),
                "保持每日记录，优先执行上面的建议并观察一周。",
                "坚持记录是改善生活习惯的第一步。",
                DISCLAIMER);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("无法序列化建议内容", e);
        }
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private record Quota(int dailyUsed, int monthlyUsed) {
    }

    private static final class QuotaExceededException extends RuntimeException {
        QuotaExceededException(String message) {
            super(message);
        }
    }
}
