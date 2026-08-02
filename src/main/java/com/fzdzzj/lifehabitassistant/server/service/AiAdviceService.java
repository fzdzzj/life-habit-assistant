package com.fzdzzj.lifehabitassistant.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fzdzzj.lifehabitassistant.pojo.*;
import com.fzdzzj.lifehabitassistant.server.dao.AiAdviceHistoryRepository;
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

    private static final String SYSTEM_PROMPT = """
            你是一个生活习惯助手的解读模块。任务：基于用户提供的脱敏健康聚合指标，生成一份温和、可执行的个人健康生活方式解读。

            成功标准：
            - 只使用输入中提供的事实，不得编造或推断新的指标。
            - 建议必须安全：不诊断疾病、不开药、不推荐极端节食或危险训练；涉及健康问题应先建议咨询医生。
            - 使用简体中文，语气鼓励但不夸张。

            输出要求：只输出一个 JSON 对象，不要 Markdown 围栏，也不要任何额外文字。字段如下：
            - periodSummary: 字符串，概括该周期整体表现。
            - riskExplanation: 字符串，解释最值得注意的风险；若没有风险写“暂无明显风险”。
            - recommendations: 字符串数组，最多 3 条可执行建议。
            - nextPeriodPlan: 字符串，下一周期 1-2 条具体行动计划。
            - encouragement: 字符串，一句鼓励。
            - disclaimer: 字符串，固定为“本建议仅作健康生活方式参考，不构成医疗诊断或治疗建议；如有健康问题请咨询医生。”
            """;

    private final HabitService habits;
    private final HealthStatisticsService statistics;
    private final RuleBasedAdviceGenerator ruleAdvice;
    private final AiAdviceHistoryRepository historyRepository;
    private final AiAdviceProperties properties;
    private final OpenAiChatClient chatClient;
    private final AiAdviceContentParser contentParser;
    private final ObjectMapper objectMapper;
    private final CurrentUser currentUser;

    public AiAdviceService(HabitService habits, HealthStatisticsService statistics,
                           RuleBasedAdviceGenerator ruleAdvice, AiAdviceHistoryRepository historyRepository,
                           AiAdviceProperties properties, OpenAiChatClient chatClient,
                           AiAdviceContentParser contentParser, ObjectMapper objectMapper, CurrentUser currentUser) {
        this.habits = habits;
        this.statistics = statistics;
        this.ruleAdvice = ruleAdvice;
        this.historyRepository = historyRepository;
        this.properties = properties;
        this.chatClient = chatClient;
        this.contentParser = contentParser;
        this.objectMapper = objectMapper;
        this.currentUser = currentUser;
    }

    @Transactional
    public AiAdviceDtos.AiAdviceResponse analysis(int days) {
        if (days < 1 || days > 366) {
            throw new IllegalArgumentException("days 必须在 1 到 366 之间");
        }
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(days - 1L);
        return generate(currentUser.require(), AiAdviceType.ANALYSIS, start, end, days,
                statistics.summarize(habits.range(start, end), end));
    }

    @Transactional
    public AiAdviceDtos.AiAdviceResponse weekly(LocalDate anyDay) {
        LocalDate start = anyDay.with(DayOfWeek.MONDAY);
        LocalDate end = start.plusDays(6);
        return generate(currentUser.require(), AiAdviceType.WEEKLY_REPORT, start, end, 7,
                statistics.summarize(habits.range(start, end), end));
    }

    @Transactional
    public AiAdviceDtos.AiAdviceResponse monthly(YearMonth month) {
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();
        int days = Math.toIntExact(end.toEpochDay() - start.toEpochDay() + 1L);
        return generate(currentUser.require(), AiAdviceType.MONTHLY_REPORT, start, end, days,
                statistics.summarize(habits.range(start, end), end));
    }

    private AiAdviceDtos.AiAdviceResponse generate(User user, AiAdviceType type, LocalDate start, LocalDate end,
                                                   int days, HealthStatistics summary) {
        AnalysisDtos.AnalysisResponse rule = ruleAdvice.generate(days, summary);
        Quota quota = quota(user);
        boolean eligible = summary.recordCount() > 0 && properties.enabled()
                && notBlank(properties.apiKey()) && notBlank(properties.model())
                && quota.dailyUsed < properties.dailyLimit() && quota.monthlyUsed < properties.monthlyLimit();
        if (!eligible) {
            return persistAndRespond(user, type, start, end, AdviceSource.RULE_FALLBACK, toContent(rule),
                    null, false, quota);
        }
        try {
            String raw = chatClient.chat(SYSTEM_PROMPT, userPrompt(days, summary, rule));
            AiAdviceDtos.AiAdviceContent content = contentParser.parse(raw);
            return persistAndRespond(user, type, start, end, AdviceSource.AI, content,
                    properties.model(), true, quota);
        } catch (RuntimeException ex) {
            log.warn("OpenAI advice failed for user {}: {}", user.getId(), ex.toString());
            return persistAndRespond(user, type, start, end, AdviceSource.RULE_FALLBACK, toContent(rule),
                    properties.model(), true, quota);
        }
    }

    private AiAdviceDtos.AiAdviceResponse persistAndRespond(User user, AiAdviceType type, LocalDate start,
                                                            LocalDate end, AdviceSource source,
                                                            AiAdviceDtos.AiAdviceContent content, String modelName,
                                                            boolean callCounted, Quota quota) {
        AiAdviceHistory saved = historyRepository.save(new AiAdviceHistory(user, type, start, end, source, modelName,
                properties.promptVersion(), toJson(content), callCounted));
        int dailyUsed = callCounted ? quota.dailyUsed + 1 : quota.dailyUsed;
        int monthlyUsed = callCounted ? quota.monthlyUsed + 1 : quota.monthlyUsed;
        return new AiAdviceDtos.AiAdviceResponse(source, content, saved.getId(), saved.getCreatedAt(),
                dailyUsed, properties.dailyLimit(), monthlyUsed, properties.monthlyLimit());
    }

    private Quota quota(User user) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dayStart = now.toLocalDate().atStartOfDay();
        LocalDateTime monthStart = now.toLocalDate().withDayOfMonth(1).atStartOfDay();
        int dailyUsed = Math.toIntExact(historyRepository.countByUserIdAndCallCountedTrueAndCreatedAtBetween(
                user.getId(), dayStart, now));
        int monthlyUsed = Math.toIntExact(historyRepository.countByUserIdAndCallCountedTrueAndCreatedAtBetween(
                user.getId(), monthStart, now));
        return new Quota(dailyUsed, monthlyUsed);
    }

    private String userPrompt(int days, HealthStatistics summary, AnalysisDtos.AnalysisResponse rule) {
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
}
