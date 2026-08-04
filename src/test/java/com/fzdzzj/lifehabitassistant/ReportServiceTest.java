package com.fzdzzj.lifehabitassistant;

import com.fzdzzj.lifehabitassistant.pojo.AnalysisDtos;
import com.fzdzzj.lifehabitassistant.pojo.AiAdviceDtos;
import com.fzdzzj.lifehabitassistant.pojo.AiAdviceHistory;
import com.fzdzzj.lifehabitassistant.pojo.AiAdviceType;
import com.fzdzzj.lifehabitassistant.pojo.AdviceSource;
import com.fzdzzj.lifehabitassistant.pojo.DailyGoals;
import com.fzdzzj.lifehabitassistant.pojo.HealthStatistics;
import com.fzdzzj.lifehabitassistant.pojo.User;
import com.fzdzzj.lifehabitassistant.server.dao.AiAdviceHistoryRepository;
import com.fzdzzj.lifehabitassistant.server.service.AdviceGenerator;
import com.fzdzzj.lifehabitassistant.server.service.AiAdviceContentParser;
import com.fzdzzj.lifehabitassistant.server.service.CurrentUser;
import com.fzdzzj.lifehabitassistant.server.service.GoalService;
import com.fzdzzj.lifehabitassistant.server.service.HealthStatisticsService;
import com.fzdzzj.lifehabitassistant.server.service.HabitService;
import com.fzdzzj.lifehabitassistant.server.service.ReportService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportServiceTest {
    private static final DailyGoals DEFAULT_GOALS = new DailyGoals(420, 540, 1500, 30, 3);

    @Test
    void weeklyShouldUseMondayToSunday() {
        HabitService habits = mock(HabitService.class);
        AdviceGenerator advice = mock(AdviceGenerator.class);
        when(habits.range(any(), any())).thenReturn(List.of());
        when(advice.generate(anyInt(), any(HealthStatistics.class), any(DailyGoals.class)))
                .thenReturn(new AnalysisDtos.AnalysisResponse(7, 0, "empty", List.of(), List.of()));

        var report = service(habits, advice).weekly(LocalDate.of(2026, 7, 19));

        assertEquals(LocalDate.of(2026, 7, 13), report.periodStart());
        assertEquals(LocalDate.of(2026, 7, 19), report.periodEnd());
        assertEquals(DEFAULT_GOALS, report.goals());
        verify(habits).range(eq(LocalDate.of(2026, 7, 13)), eq(LocalDate.of(2026, 7, 19)));
    }

    @Test
    void monthlyShouldUseLeapYearMonthEnd() {
        HabitService habits = mock(HabitService.class);
        AdviceGenerator advice = mock(AdviceGenerator.class);
        when(habits.range(any(), any())).thenReturn(List.of());
        when(advice.generate(anyInt(), any(HealthStatistics.class), any(DailyGoals.class)))
                .thenReturn(new AnalysisDtos.AnalysisResponse(29, 0, "empty", List.of(), List.of()));

        var report = service(habits, advice).monthly(YearMonth.of(2024, 2));

        assertEquals(LocalDate.of(2024, 2, 29), report.periodEnd());
        verify(habits).range(eq(LocalDate.of(2024, 2, 1)), eq(LocalDate.of(2024, 2, 29)));
    }

    @Test
    void reportShouldAttachLatestSavedAdviceForItsPeriod() {
        HabitService habits = mock(HabitService.class);
        AdviceGenerator advice = mock(AdviceGenerator.class);
        AiAdviceHistoryRepository history = mock(AiAdviceHistoryRepository.class);
        AiAdviceContentParser parser = mock(AiAdviceContentParser.class);
        CurrentUser currentUser = mock(CurrentUser.class);
        User user = new User("demo", "hash");
        when(currentUser.require()).thenReturn(user);
        when(habits.range(any(), any())).thenReturn(List.of());
        when(advice.generate(anyInt(), any(HealthStatistics.class), any(DailyGoals.class)))
                .thenReturn(new AnalysisDtos.AnalysisResponse(7, 0, "empty", List.of(), List.of()));
        AiAdviceDtos.AiAdviceContent content = new AiAdviceDtos.AiAdviceContent(
                "稳定", "无风险", List.of("保持记录"), "继续", "加油", "仅供健康参考");
        AiAdviceHistory saved = new AiAdviceHistory(user, AiAdviceType.WEEKLY_REPORT,
                LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 19), AdviceSource.AI,
                "gpt-demo", "v1", "{}", true);
        when(history.findFirstByUserIdAndAdviceTypeAndPeriodStartAndPeriodEndOrderByCreatedAtDesc(
                eq(null), any(), any(), any())).thenReturn(java.util.Optional.of(saved));
        when(parser.parse(any())).thenReturn(content);

        var report = new ReportService(habits,
                new HealthStatisticsService(TestDrinkRules.defaults()),
                advice, history, parser, currentUser, goals()).weekly(LocalDate.of(2026, 7, 19));

        assertEquals(AdviceSource.AI, report.aiAdvice().source());
        assertSame(content, report.aiAdvice().content());
    }

    @Test
    void reportWithoutSavedAdviceShouldReturnNullAiAdvice() {
        HabitService habits = mock(HabitService.class);
        AdviceGenerator advice = mock(AdviceGenerator.class);
        AiAdviceHistoryRepository history = mock(AiAdviceHistoryRepository.class);
        AiAdviceContentParser parser = mock(AiAdviceContentParser.class);
        CurrentUser currentUser = mock(CurrentUser.class);
        when(currentUser.require()).thenReturn(new User("demo", "hash"));
        when(habits.range(any(), any())).thenReturn(List.of());
        when(advice.generate(anyInt(), any(HealthStatistics.class), any(DailyGoals.class)))
                .thenReturn(new AnalysisDtos.AnalysisResponse(7, 0, "empty", List.of(), List.of()));
        when(history.findFirstByUserIdAndAdviceTypeAndPeriodStartAndPeriodEndOrderByCreatedAtDesc(
                any(), any(), any(), any())).thenReturn(java.util.Optional.empty());

        var report = new ReportService(habits,
                new HealthStatisticsService(TestDrinkRules.defaults()),
                advice, history, parser, currentUser, goals()).weekly(LocalDate.of(2026, 7, 19));

        assertNull(report.aiAdvice());
    }

    private ReportService service(HabitService habits, AdviceGenerator advice) {
        AiAdviceHistoryRepository history = mock(AiAdviceHistoryRepository.class);
        AiAdviceContentParser parser = mock(AiAdviceContentParser.class);
        CurrentUser currentUser = mock(CurrentUser.class);
        when(currentUser.require()).thenReturn(new User("demo", "hash"));
        when(history.findFirstByUserIdAndAdviceTypeAndPeriodStartAndPeriodEndOrderByCreatedAtDesc(
                any(), any(), any(), any())).thenReturn(java.util.Optional.empty());
        return new ReportService(habits,
                new HealthStatisticsService(TestDrinkRules.defaults()),
                advice, history, parser, currentUser, goals());
    }

    private GoalService goals() {
        GoalService goals = mock(GoalService.class);
        when(goals.get()).thenReturn(DEFAULT_GOALS);
        return goals;
    }
}
