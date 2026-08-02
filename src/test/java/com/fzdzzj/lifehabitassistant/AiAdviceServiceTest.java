package com.fzdzzj.lifehabitassistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fzdzzj.lifehabitassistant.pojo.*;
import com.fzdzzj.lifehabitassistant.server.dao.AiAdviceHistoryRepository;
import com.fzdzzj.lifehabitassistant.server.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AiAdviceServiceTest {
    private static final String AI_JSON = """
            {"periodSummary":"整体稳定","riskExplanation":"睡眠略不足",
            "recommendations":["固定就寝时间","晚餐少用屏幕"],
            "nextPeriodPlan":"每天记录","encouragement":"继续保持","disclaimer":"仅供健康参考"}
            """;

    private HabitService habits;
    private AiAdviceHistoryRepository history;
    private OpenAiChatClient chatClient;
    private CurrentUser currentUser;
    private User user;
    private AiAdviceService service;

    @BeforeEach
    void setUp() {
        habits = mock(HabitService.class);
        history = mock(AiAdviceHistoryRepository.class);
        chatClient = mock(OpenAiChatClient.class);
        currentUser = mock(CurrentUser.class);
        user = mock(User.class);
        when(user.getId()).thenReturn(42L);
        when(currentUser.require()).thenReturn(user);
        when(history.countByUserIdAndCallCountedTrueAndCreatedAtBetween(any(), any(), any())).thenReturn(0L);
        when(history.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service = service(enabled(true, 3, 30));
    }

    @Test
    void disabledShouldFallBackWithoutCallingProvider() {
        service = service(enabled(false, 3, 30));
        givenOneRecord();

        AiAdviceDtos.AiAdviceResponse response = service.analysis(7);

        assertEquals(AdviceSource.RULE_FALLBACK, response.source());
        verifyNoInteractions(chatClient);
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
    }

    @Test
    void successfulCallShouldReturnAiContentAndCountQuota() {
        givenOneRecord();
        when(chatClient.chat(any(), any())).thenReturn(AI_JSON);

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
        verify(chatClient).chat(any(), contains("脱敏健康聚合指标"));
    }

    @Test
    void providerFailureShouldFallBackAndCountTheAttempt() {
        givenOneRecord();
        when(chatClient.chat(any(), any())).thenThrow(new IllegalStateException("provider timeout"));

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
        when(history.countByUserIdAndCallCountedTrueAndCreatedAtBetween(any(), any(), any())).thenReturn(3L);

        AiAdviceDtos.AiAdviceResponse response = service.analysis(7);

        assertEquals(AdviceSource.RULE_FALLBACK, response.source());
        assertEquals(3, response.dailyUsed());
        assertEquals(3, response.dailyLimit());
        verifyNoInteractions(chatClient);
        assertFalse(savedHistory().isCallCounted());
    }

    @Test
    void monthlyQuotaExhaustedShouldFallBackWithoutCallingProvider() {
        givenOneRecord();
        when(history.countByUserIdAndCallCountedTrueAndCreatedAtBetween(any(), any(), any())).thenReturn(30L);

        AiAdviceDtos.AiAdviceResponse response = service.analysis(7);

        assertEquals(AdviceSource.RULE_FALLBACK, response.source());
        assertEquals(30, response.monthlyUsed());
        verifyNoInteractions(chatClient);
    }

    @Test
    void quotaShouldBeCountedPerUser() {
        givenOneRecord();
        when(chatClient.chat(any(), any())).thenReturn(AI_JSON);

        service.analysis(7);

        ArgumentCaptor<Long> userId = ArgumentCaptor.forClass(Long.class);
        verify(history, atLeastOnce()).countByUserIdAndCallCountedTrueAndCreatedAtBetween(
                userId.capture(), any(), any());
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

    private AiAdviceService service(AiAdviceProperties properties) {
        HealthThresholds thresholds = new HealthThresholds(420, 540, 1500, 30, 3);
        return new AiAdviceService(habits,
                new HealthStatisticsService(thresholds, TestDrinkRules.defaults()),
                new RuleBasedAdviceGenerator(thresholds, TestDrinkRules.defaults()),
                history, properties, chatClient, new AiAdviceContentParser(new ObjectMapper()),
                new ObjectMapper(), currentUser);
    }

    private AiAdviceProperties enabled(boolean enabled, int dailyLimit, int monthlyLimit) {
        return new AiAdviceProperties(enabled, "sk-test", "gpt-demo",
                "https://api.openai.com/v1", dailyLimit, monthlyLimit, 30, "v1");
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
