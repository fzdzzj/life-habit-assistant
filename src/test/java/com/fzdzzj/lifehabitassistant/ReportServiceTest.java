package com.fzdzzj.lifehabitassistant;

import com.fzdzzj.lifehabitassistant.pojo.AnalysisDtos;
import com.fzdzzj.lifehabitassistant.server.service.AdviceGenerator;
import com.fzdzzj.lifehabitassistant.server.service.AnalysisService;
import com.fzdzzj.lifehabitassistant.server.service.HealthThresholds;
import com.fzdzzj.lifehabitassistant.server.service.ReportService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportServiceTest {
    @Test
    void weeklyShouldUseMondayToSunday() {
        AnalysisService analysis = mock(AnalysisService.class);
        AdviceGenerator advice = mock(AdviceGenerator.class);
        when(analysis.between(any(), any())).thenReturn(List.of());
        when(advice.generate(anyInt(), any())).thenReturn(new AnalysisDtos.AnalysisResponse(7, 0, "empty", List.of(), List.of()));

        var report = new ReportService(analysis, advice, new HealthThresholds(420, 540, 1500, 30, 3), TestDrinkRules.defaults())
                .weekly(LocalDate.of(2026, 7, 19));

        assertEquals(LocalDate.of(2026, 7, 13), report.periodStart());
        assertEquals(LocalDate.of(2026, 7, 19), report.periodEnd());
        verify(analysis).between(eq(LocalDate.of(2026, 7, 13)), eq(LocalDate.of(2026, 7, 19)));
    }

    @Test
    void monthlyShouldUseLeapYearMonthEnd() {
        AnalysisService analysis = mock(AnalysisService.class);
        AdviceGenerator advice = mock(AdviceGenerator.class);
        when(analysis.between(any(), any())).thenReturn(List.of());
        when(advice.generate(anyInt(), any())).thenReturn(new AnalysisDtos.AnalysisResponse(29, 0, "empty", List.of(), List.of()));

        var report = new ReportService(analysis, advice, new HealthThresholds(420, 540, 1500, 30, 3), TestDrinkRules.defaults())
                .monthly(YearMonth.of(2024, 2));

        assertEquals(LocalDate.of(2024, 2, 29), report.periodEnd());
        verify(analysis).between(eq(LocalDate.of(2024, 2, 1)), eq(LocalDate.of(2024, 2, 29)));
    }
}
