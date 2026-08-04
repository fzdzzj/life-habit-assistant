package com.fzdzzj.lifehabitassistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fzdzzj.lifehabitassistant.config.AiAdviceCache;
import com.fzdzzj.lifehabitassistant.config.ReportCache;
import com.fzdzzj.lifehabitassistant.config.ReportProperties;
import com.fzdzzj.lifehabitassistant.config.UserCacheEvictor;
import com.fzdzzj.lifehabitassistant.pojo.*;
import com.fzdzzj.lifehabitassistant.server.dao.AiAdviceHistoryRepository;
import com.fzdzzj.lifehabitassistant.server.dao.AiQuotaUsageRepository;
import com.fzdzzj.lifehabitassistant.server.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AiAdviceServiceTest {
    private HabitService habits;
    private AiAdviceHistoryRepository history;
    private AiQuotaUsageRepository quota;
    private OpenAiChatClient chatClient;
    private CurrentUser currentUser;
    private GoalService goals;
    private User user;
    private AiAdviceService service;
    private AiAdviceCache adviceCache;
    private UserCacheEvictor cacheEvictor;

    @BeforeEach
    void setUp() {
        habits = mock(HabitService.class);
        history = mock(AiAdviceHistoryRepository.class);
        quota = mock(AiQuotaUsageRepository.class);
        chatClient = mock(OpenAiChatClient.class);
        currentUser = mock(CurrentUser.class);
        goals = mock(GoalService.class);
        when(goals.get()).thenReturn(new DailyGoals(420, 540, 1500, 30, 3));
        user = mock(User.class);
        when(user.getId()).thenReturn(42L);
        when(user.getAiDailyLimit()).thenReturn(null);
        when(user.getAiMonthlyLimit()).thenReturn(null);
        when(currentUser.require()).thenReturn(user);
        when(history.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(quota.incrementIfBelowLimit(any(), any(), any(), anyInt(), any())).thenReturn(1);
        when(quota.findUsedCount(any(), any(), any())).thenReturn(Optional.empty());
        service = service(enabled(true, 3, 30));
    }

    @Test
    void disabledShouldFallBackWithoutCallingProvider() {
        service = service(enabled(false, 3, 30));
        givenOneRecord();

        AiAdviceDtos.AiAdviceResponse response = service.analysis(7);

        assertEquals(AdviceSource.RULE_FALLBACK, response.source());
        verifyNoInteractions(chatClient);
        verify(quota, never()).incrementIfBelowLimit(any(), any(), any(), anyInt(), any());
        AiAdviceHistory saved = savedHistory();
        assertEquals(AdviceSource.RULE_FALLBACK, saved.getSource());
        assertFalse(saved.isCallCounted());
        assertEquals(0, response.dailyUsed());
    }

    @Test
    void emptyPeriodShouldFallBackWithoutCallingProvider() {
        givenNoRecords();

        AiAdviceDtos.AiAdviceResponse response = service.analysis(7);

        assertEquals(AdviceSource.RULE_FALLBACK, response.source());
        verifyNoInteractions(chatClient);
        verify(quota, never()).incrementIfBelowLimit(any(), any(), any(), anyInt(), any());
    }

    @Test
    void successfulCallShouldReturnAiContentAndCountQuota() {
        givenOneRecord();
        when(chatClient.chatStructured(any(), any(), eq(AiAdviceDtos.AiAdviceContent.class)))
                .thenReturn(aiContent());
        quotaUsed(1);

        AiAdviceDtos.AiAdviceResponse response = service.analysis(7);

        assertEquals(AdviceSource.AI, response.source());
        assertEquals("整体稳定", response.content().periodSummary());
        assertEquals(2, response.content().recommendations().size());
        assertEquals(1, response.dailyUsed());
        assertEquals(1, response.monthlyUsed());
        AiAdviceHistory saved = savedHistory();
        assertEquals(AdviceSource.AI, saved.getSource());
        assertEquals("gpt-demo", saved.getModelName());
        assertTrue(saved.isCallCounted());
        assertTrue(saved.getContent().contains("periodSummary"));
        verify(chatClient).chatStructured(any(), contains("脱敏健康聚合指标"),
                eq(AiAdviceDtos.AiAdviceContent.class));
    }

    @Test
    void providerFailureShouldFallBackAndCountTheAttempt() {
        givenOneRecord();
        when(chatClient.chatStructured(any(), any(), eq(AiAdviceDtos.AiAdviceContent.class)))
                .thenThrow(new IllegalStateException("provider timeout"));
        quotaUsed(1);

        AiAdviceDtos.AiAdviceResponse response = service.analysis(7);

        assertEquals(AdviceSource.RULE_FALLBACK, response.source());
        assertEquals(1, response.dailyUsed());
        AiAdviceHistory saved = savedHistory();
        assertEquals(AdviceSource.RULE_FALLBACK, saved.getSource());
        assertTrue(saved.isCallCounted());
    }

    @Test
    void dailyQuotaExhaustedShouldFallBackWithoutCallingProvider() {
        givenOneRecord();
        when(quota.incrementIfBelowLimit(any(), eq("DAY"), any(), anyInt(), any())).thenReturn(0);

        AiAdviceDtos.AiAdviceResponse response = service.analysis(7);

        assertEquals(AdviceSource.RULE_FALLBACK, response.source());
        assertEquals(3, response.dailyLimit());
        verifyNoInteractions(chatClient);
        assertFalse(savedHistory().isCallCounted());
    }

    @Test
    void monthlyQuotaExhaustedShouldFallBackWithoutCallingProvider() {
        givenOneRecord();
        when(quota.incrementIfBelowLimit(any(), eq("DAY"), any(), anyInt(), any())).thenReturn(1);
        when(quota.incrementIfBelowLimit(any(), eq("MONTH"), any(), anyInt(), any())).thenReturn(0);

        AiAdviceDtos.AiAdviceResponse response = service.analysis(7);

        assertEquals(AdviceSource.RULE_FALLBACK, response.source());
        assertEquals(30, response.monthlyLimit());
        verifyNoInteractions(chatClient);
    }

    @Test
    void perUserQuotaOverrideShouldReplaceGlobalLimits() {
        givenOneRecord();
        when(chatClient.chatStructured(any(), any(), eq(AiAdviceDtos.AiAdviceContent.class)))
                .thenReturn(aiContent());
        when(user.getAiDailyLimit()).thenReturn(5);
        when(user.getAiMonthlyLimit()).thenReturn(60);
        quotaUsed(1);

        AiAdviceDtos.AiAdviceResponse response = service.analysis(7);

        assertEquals(AdviceSource.AI, response.source());
        assertEquals(5, response.dailyLimit());
        assertEquals(60, response.monthlyLimit());
        verify(quota).incrementIfBelowLimit(eq(42L), eq("DAY"), any(), eq(5), any());
        verify(quota).incrementIfBelowLimit(eq(42L), eq("MONTH"), any(), eq(60), any());
    }

    @Test
    void quotaShouldBeCountedPerUser() {
        givenOneRecord();
        when(chatClient.chatStructured(any(), any(), eq(AiAdviceDtos.AiAdviceContent.class)))
                .thenReturn(aiContent());

        service.analysis(7);

        ArgumentCaptor<Long> userId = ArgumentCaptor.forClass(Long.class);
        verify(quota, atLeastOnce()).incrementIfBelowLimit(userId.capture(), any(), any(), anyInt(), any());
        assertEquals(List.of(42L), userId.getAllValues().stream().distinct().toList());
        ArgumentCaptor<AiAdviceHistory> saved = ArgumentCaptor.forClass(AiAdviceHistory.class);
        verify(history).save(saved.capture());
        assertSame(user, saved.getValue().getUser());
    }

    @Test
    void weeklyShouldUseNaturalWeekBounds() {
        givenNoRecords();
        service.weekly(LocalDate.of(2026, 7, 19));

        ArgumentCaptor<AiAdviceHistory> saved = ArgumentCaptor.forClass(AiAdviceHistory.class);
        verify(history).save(saved.capture());
        assertEquals(LocalDate.of(2026, 7, 13), saved.getValue().getPeriodStart());
        assertEquals(LocalDate.of(2026, 7, 19), saved.getValue().getPeriodEnd());
        assertEquals(AiAdviceType.WEEKLY_REPORT, saved.getValue().getAdviceType());
        verify(habits).range(eq(LocalDate.of(2026, 7, 13)), eq(LocalDate.of(2026, 7, 19)));
    }

    @Test
    void secondSamePeriodRequestShouldHitCacheWithoutModelOrHistory() {
        givenOneRecord();
        when(chatClient.chatStructured(any(), any(), eq(AiAdviceDtos.AiAdviceContent.class)))
                .thenReturn(aiContent());
        quotaUsed(1);

        AiAdviceDtos.AiAdviceResponse first = service.analysis(7);
        AiAdviceDtos.AiAdviceResponse second = service.analysis(7);

        assertFalse(first.cached());
        assertTrue(second.cached());
        assertEquals(first.historyId(), second.historyId());
        assertEquals(first.content(), second.content());
        verify(chatClient, times(1)).chatStructured(any(), any(),
                eq(AiAdviceDtos.AiAdviceContent.class));
        verify(history, times(1)).save(any());
        verify(quota, times(1)).incrementIfBelowLimit(eq(42L), eq("DAY"), any(), anyInt(), any());
    }

    @Test
    void refreshShouldForceRegenerationAndUpdateCache() {
        givenOneRecord();
        when(chatClient.chatStructured(any(), any(), eq(AiAdviceDtos.AiAdviceContent.class)))
                .thenReturn(aiContent());
        quotaUsed(1);

        service.analysis(7);
        AiAdviceDtos.AiAdviceResponse refreshed = service.analysis(7, true);

        assertFalse(refreshed.cached());
        verify(chatClient, times(2)).chatStructured(any(), any(),
                eq(AiAdviceDtos.AiAdviceContent.class));
    }

    @Test
    void userDataChangeShouldInvalidateAdviceCache() {
        givenOneRecord();
        when(chatClient.chatStructured(any(), any(), eq(AiAdviceDtos.AiAdviceContent.class)))
                .thenReturn(aiContent());
        quotaUsed(1);

        service.analysis(7);
        assertTrue(service.analysis(7).cached());

        cacheEvictor.evictAll(42L);

        assertFalse(service.analysis(7).cached());
        verify(chatClient, times(2)).chatStructured(any(), any(),
                eq(AiAdviceDtos.AiAdviceContent.class));
    }

    @Test
    void ruleFallbackShouldNotBeCached() {
        givenOneRecord();
        service = service(enabled(false, 3, 30));

        service.analysis(7);

        LocalDate end = LocalDate.now();
        assertTrue(adviceCache.get(42L, AiAdviceType.ANALYSIS, end.minusDays(6), end, "v1").isEmpty());
    }

    private AiAdviceService service(AiAdviceProperties properties) {
        adviceCache = new AiAdviceCache(properties);
        cacheEvictor = new UserCacheEvictor(mock(ReportCache.class), adviceCache);
        return new AiAdviceService(habits,
                new HealthStatisticsService(TestDrinkRules.defaults()),
                new RuleBasedAdviceGenerator(TestDrinkRules.defaults()),
                history, new AiQuotaService(quota, properties), properties, new AiSystemPromptLoader(properties),
                chatClient, new AiAdviceContentParser(new ObjectMapper()),
                new ObjectMapper(), currentUser, goals, cacheEvictor, adviceCache);
    }

    private void quotaUsed(int used) {
        LocalDateTime now = LocalDateTime.now();
        when(quota.findUsedCount(eq(42L), eq("DAY"), eq(now.toLocalDate().toString())))
                .thenReturn(Optional.of(used));
        when(quota.findUsedCount(eq(42L), eq("MONTH"), eq(java.time.YearMonth.now().toString())))
                .thenReturn(Optional.of(used));
    }

    private AiAdviceProperties enabled(boolean enabled, int dailyLimit, int monthlyLimit) {
        return new AiAdviceProperties(enabled, "sk-test", "gpt-demo",
                "https://api.openai.com/v1", dailyLimit, monthlyLimit, 30, "v1",
                Duration.ofMinutes(10), 128);
    }

    private AiAdviceDtos.AiAdviceContent aiContent() {
        return new AiAdviceDtos.AiAdviceContent("整体稳定", "睡眠略不足",
                List.of("固定就寝时间", "晚餐少用屏幕"), "每天记录", "继续保持", "仅供健康参考");
    }

    private void givenOneRecord() {
        LocalDate today = LocalDate.now();
        HabitRecord record = new HabitRecord(user, today, 4, null);
        record.addSleepSession(new SleepSession(record, SleepType.NIGHT,
                today.minusDays(1).atTime(23, 0), today.atTime(7, 0)));
        record.addExerciseSession(new ExerciseSession(record, ExerciseType.WALK, null,
                ExerciseIntensity.MEDIUM, 30, today.atTime(18, 0), null, null, null));
        record.addDrinkRecord(new DrinkRecord(record, DrinkType.WATER, null, 1500,
                today.atTime(12, 0), null));
        when(habits.range(any(), any())).thenReturn(List.of(record));
    }

    private void givenNoRecords() {
        when(habits.range(any(), any())).thenReturn(List.of());
    }

    private AiAdviceHistory savedHistory() {
        ArgumentCaptor<AiAdviceHistory> captor = ArgumentCaptor.forClass(AiAdviceHistory.class);
        verify(history).save(captor.capture());
        return captor.getValue();
    }
}
