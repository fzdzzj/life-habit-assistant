package com.fzdzzj.lifehabitassistant.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fzdzzj.lifehabitassistant.pojo.*;
import com.fzdzzj.lifehabitassistant.config.AiAdviceCache;
import com.fzdzzj.lifehabitassistant.config.UserCacheEvictor;
import com.fzdzzj.lifehabitassistant.server.dao.AiAdviceHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
    private final AiQuotaService quotaService;
    private final AiAdviceProperties properties;
    private final AiSystemPromptLoader promptLoader;
    private final OpenAiChatClient chatClient;
    private final AiAdviceContentParser contentParser;
    private final ObjectMapper objectMapper;
    private final CurrentUser currentUser;
    private final GoalService goals;
    private final UserCacheEvictor cacheEvictor;
    private final AiAdviceCache aiAdviceCache;

    public AiAdviceService(HabitService habits, HealthStatisticsService statistics,
                           RuleBasedAdviceGenerator ruleAdvice, AiAdviceHistoryRepository historyRepository,
                           AiQuotaService quotaService, AiAdviceProperties properties,
                           AiSystemPromptLoader promptLoader, OpenAiChatClient chatClient,
                           AiAdviceContentParser contentParser, ObjectMapper objectMapper, CurrentUser currentUser,
                           GoalService goals, UserCacheEvictor cacheEvictor, AiAdviceCache aiAdviceCache) {
        this.habits = habits;
        this.statistics = statistics;
        this.ruleAdvice = ruleAdvice;
        this.historyRepository = historyRepository;
        this.quotaService = quotaService;
        this.properties = properties;
        this.promptLoader = promptLoader;
        this.chatClient = chatClient;
        this.contentParser = contentParser;
        this.objectMapper = objectMapper;
        this.currentUser = currentUser;
        this.goals = goals;
        this.cacheEvictor = cacheEvictor;
        this.aiAdviceCache = aiAdviceCache;
    }

    @Transactional
    public AiAdviceDtos.AiAdviceResponse analysis(int days) {
        return analysis(days, false);
    }

    @Transactional
    public AiAdviceDtos.AiAdviceResponse analysis(int days, boolean refresh) {
        if (days < 1 || days > 366) {
            throw new IllegalArgumentException("days 必须在 1 到 366 之间");
        }
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(days - 1L);
        var effectiveGoals = goals.get();
        return generate(currentUser.require(), AiAdviceType.ANALYSIS, start, end, days,
                statistics.summarize(habits.range(start, end), end, effectiveGoals), effectiveGoals, refresh);
    }

    @Transactional
    public AiAdviceDtos.AiAdviceResponse weekly(LocalDate anyDay) {
        return weekly(anyDay, false);
    }

    @Transactional
    public AiAdviceDtos.AiAdviceResponse weekly(LocalDate anyDay, boolean refresh) {
        LocalDate start = anyDay.with(DayOfWeek.MONDAY);
        LocalDate end = start.plusDays(6);
        var effectiveGoals = goals.get();
        return generate(currentUser.require(), AiAdviceType.WEEKLY_REPORT, start, end, 7,
                statistics.summarize(habits.range(start, end), end, effectiveGoals), effectiveGoals, refresh);
    }

    @Transactional
    public AiAdviceDtos.AiAdviceResponse monthly(YearMonth month) {
        return monthly(month, false);
    }

    @Transactional
    public AiAdviceDtos.AiAdviceResponse monthly(YearMonth month, boolean refresh) {
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();
        int days = Math.toIntExact(end.toEpochDay() - start.toEpochDay() + 1L);
        var effectiveGoals = goals.get();
        return generate(currentUser.require(), AiAdviceType.MONTHLY_REPORT, start, end, days,
                statistics.summarize(habits.range(start, end), end, effectiveGoals), effectiveGoals, refresh);
    }

    private AiAdviceDtos.AiAdviceResponse generate(User user, AiAdviceType type, LocalDate start, LocalDate end,
                                                   int days, HealthStatistics summary, DailyGoals goals,
                                                   boolean refresh) {
        if (!refresh) {
            Optional<AiAdviceCache.CachedAdvice> cached = aiAdviceCache.get(user.getId(), type, start, end,
                    properties.promptVersion());
            if (cached.isPresent()) {
                AiQuotaService.QuotaSnapshot quota = quotaService.usage(user);
                AiAdviceCache.CachedAdvice hit = cached.get();
                return new AiAdviceDtos.AiAdviceResponse(hit.source(), hit.content(), hit.historyId(),
                        hit.createdAt(), quota.dailyUsed(), quota.dailyLimit(),
                        quota.monthlyUsed(), quota.monthlyLimit(), true);
            }
        }
        AnalysisDtos.AnalysisResponse rule = ruleAdvice.generate(days, summary, goals);
        AiAdviceDtos.AiAdviceResponse response;
        if (!eligible(properties, summary)) {
            response = persistAndRespond(user, type, start, end, AdviceSource.RULE_FALLBACK, toContent(rule),
                    null, false);
        } else {
            try {
                quotaService.occupy(user);
            } catch (AiQuotaService.QuotaExceededException ex) {
                response = persistAndRespond(user, type, start, end, AdviceSource.RULE_FALLBACK, toContent(rule),
                        null, false);
                return response;
            }
            try {
                AiAdviceDtos.AiAdviceContent content = chatClient.chatStructured(promptLoader.load(),
                        userPrompt(days, summary, rule, goals), AiAdviceDtos.AiAdviceContent.class);
                if (content == null) {
                    throw new IllegalStateException("structured output is null");
                }
                response = persistAndRespond(user, type, start, end, AdviceSource.AI, content,
                        properties.model(), true);
            } catch (RuntimeException ex) {
                log.warn("OpenAI advice failed for user {}: {}", user.getId(), ex.toString());
                response = persistAndRespond(user, type, start, end, AdviceSource.RULE_FALLBACK, toContent(rule),
                        properties.model(), true);
            }
        }
        if (response.source() == AdviceSource.AI) {
            aiAdviceCache.put(user.getId(), type, start, end, properties.promptVersion(),
                    new AiAdviceCache.CachedAdvice(response.source(), response.content(),
                            response.historyId(), response.createdAt()));
        }
        return response;
    }

    private AiAdviceDtos.AiAdviceResponse persistAndRespond(User user, AiAdviceType type, LocalDate start,
                                                            LocalDate end, AdviceSource source,
                                                            AiAdviceDtos.AiAdviceContent content, String modelName,
                                                            boolean callCounted) {
        AiAdviceHistory saved = historyRepository.save(new AiAdviceHistory(user, type, start, end, source, modelName,
                properties.promptVersion(), toJson(content), callCounted));
        cacheEvictor.evictReports(user.getId());
        AiQuotaService.QuotaSnapshot quota = quotaService.usage(user);
        return new AiAdviceDtos.AiAdviceResponse(source, content, saved.getId(), saved.getCreatedAt(),
                quota.dailyUsed(), quota.dailyLimit(), quota.monthlyUsed(), quota.monthlyLimit(), false);
    }

    private boolean eligible(AiAdviceProperties properties, HealthStatistics summary) {
        return summary.recordCount() > 0 && properties.enabled()
                && notBlank(properties.apiKey()) && notBlank(properties.model());
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

}
