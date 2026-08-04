package com.fzdzzj.lifehabitassistant;

import com.fzdzzj.lifehabitassistant.pojo.AnalysisDtos;
import com.fzdzzj.lifehabitassistant.pojo.DailyGoals;
import com.fzdzzj.lifehabitassistant.pojo.ExportFormat;
import com.fzdzzj.lifehabitassistant.pojo.ExportReportType;
import com.fzdzzj.lifehabitassistant.pojo.ExportTask;
import com.fzdzzj.lifehabitassistant.pojo.ExportTaskStatus;
import com.fzdzzj.lifehabitassistant.pojo.ReportDtos;
import com.fzdzzj.lifehabitassistant.pojo.User;
import com.fzdzzj.lifehabitassistant.server.dao.ExportTaskRepository;
import com.fzdzzj.lifehabitassistant.server.service.ExportTaskWorker;
import com.fzdzzj.lifehabitassistant.server.service.ReportExporter;
import com.fzdzzj.lifehabitassistant.server.service.ReportService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ExportTaskWorkerTest {
    private static final DailyGoals GOALS = new DailyGoals(420, 540, 1500, 30, 3);

    @Test
    void skipsTaskAlreadyClaimedByAnotherWorker() {
        ExportTaskRepository tasks = mock(ExportTaskRepository.class);
        ReportService reports = mock(ReportService.class);
        ReportExporter exporter = mock(ReportExporter.class);
        when(tasks.markRunning(eq(1L), any())).thenReturn(0);
        ExportTaskWorker worker = worker(tasks, reports, exporter);

        worker.generate(1L);

        verify(tasks, never()).findWithUserById(any());
        verifyNoInteractions(reports, exporter);
    }

    @Test
    void generatesFileAndMarksTaskSucceeded() {
        ExportTaskRepository tasks = mock(ExportTaskRepository.class);
        ReportService reports = mock(ReportService.class);
        ReportExporter exporter = mock(ReportExporter.class);
        User user = new User("demo", "hash");
        ExportTask task = new ExportTask(user, ExportReportType.CUSTOM, ExportFormat.XLSX,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30));
        ReportDtos.ReportResponse report = report();
        when(tasks.markRunning(eq(1L), any())).thenReturn(1);
        when(tasks.findWithUserById(1L)).thenReturn(Optional.of(task));
        when(reports.customForUser(user, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30))).thenReturn(report);
        when(exporter.xlsx(report)).thenReturn(new byte[]{1, 2, 3});
        ExportTaskWorker worker = worker(tasks, reports, exporter);

        worker.generate(1L);

        assertEquals(ExportTaskStatus.SUCCEEDED, task.getStatus());
        assertEquals("life-habit-custom-2026-01-01_2026-06-30.xlsx", task.getFileName());
        assertArrayEquals(new byte[]{1, 2, 3}, task.getFileContent());
        verify(tasks).save(task);
    }

    @Test
    void failureMarksTaskFailedWithMessage() {
        ExportTaskRepository tasks = mock(ExportTaskRepository.class);
        ReportService reports = mock(ReportService.class);
        ReportExporter exporter = mock(ReportExporter.class);
        User user = new User("demo", "hash");
        ExportTask task = new ExportTask(user, ExportReportType.CUSTOM, ExportFormat.XLSX,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30));
        when(tasks.markRunning(eq(1L), any())).thenReturn(1);
        when(tasks.findWithUserById(1L)).thenReturn(Optional.of(task));
        when(reports.customForUser(any(), any(), any())).thenThrow(new IllegalStateException("boom"));
        ExportTaskWorker worker = worker(tasks, reports, exporter);

        worker.generate(1L);

        assertEquals(ExportTaskStatus.FAILED, task.getStatus());
        assertEquals("boom", task.getErrorMessage());
        verify(tasks).save(task);
    }

    private ExportTaskWorker worker(ExportTaskRepository tasks, ReportService reports, ReportExporter exporter) {
        return new ExportTaskWorker(tasks, reports, exporter);
    }

    private ReportDtos.ReportResponse report() {
        var trend = new AnalysisDtos.DailyTrend(LocalDate.of(2026, 1, 1), 7.5, 4, 30, 1600, 0, true);
        return new ReportDtos.ReportResponse("custom", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30),
                1, 7.5, 4, 30, 1600, 0, 100, GOALS, List.of(trend), List.of(), List.of(), List.of(), null);
    }
}
