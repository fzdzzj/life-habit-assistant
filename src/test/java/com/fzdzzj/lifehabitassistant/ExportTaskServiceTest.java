package com.fzdzzj.lifehabitassistant;

import com.fzdzzj.lifehabitassistant.common.ApiException;
import com.fzdzzj.lifehabitassistant.config.ExportProperties;
import com.fzdzzj.lifehabitassistant.pojo.ExportFormat;
import com.fzdzzj.lifehabitassistant.pojo.ExportReportType;
import com.fzdzzj.lifehabitassistant.pojo.ExportTask;
import com.fzdzzj.lifehabitassistant.pojo.ExportTaskDtos;
import com.fzdzzj.lifehabitassistant.pojo.ExportTaskStatus;
import com.fzdzzj.lifehabitassistant.pojo.User;
import com.fzdzzj.lifehabitassistant.server.dao.ExportTaskRepository;
import com.fzdzzj.lifehabitassistant.server.service.CurrentUser;
import com.fzdzzj.lifehabitassistant.server.service.ExportTaskService;
import com.fzdzzj.lifehabitassistant.server.service.ExportTaskWorker;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExportTaskServiceTest {
    private static final ExportProperties PROPERTIES = new ExportProperties(1826, 5);

    @Test
    void createCustomTaskPersistsPendingAndSubmitsWorker() {
        ExportTaskRepository tasks = mock(ExportTaskRepository.class);
        ExportTaskWorker worker = mock(ExportTaskWorker.class);
        CurrentUser currentUser = currentUser();
        when(tasks.countByUserIdAndStatus(42L, ExportTaskStatus.PENDING)).thenReturn(0L);
        when(tasks.save(any(ExportTask.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ExportTaskService service = service(tasks, worker, currentUser);
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 6, 30);

        ExportTaskDtos.ExportTaskResponse response = service.create(
                ExportReportType.CUSTOM, ExportFormat.XLSX, null, null, start, end);

        assertEquals(ExportReportType.CUSTOM, response.reportType());
        assertEquals(ExportFormat.XLSX, response.format());
        assertEquals(start, response.periodStart());
        assertEquals(end, response.periodEnd());
        assertEquals(ExportTaskStatus.PENDING, response.status());
        verify(tasks).save(any(ExportTask.class));
        verify(worker).generate(any());
    }

    @Test
    void weeklyTaskUsesNaturalWeekBounds() {
        ExportTaskRepository tasks = mock(ExportTaskRepository.class);
        ExportTaskWorker worker = mock(ExportTaskWorker.class);
        when(tasks.countByUserIdAndStatus(42L, ExportTaskStatus.PENDING)).thenReturn(0L);
        when(tasks.save(any(ExportTask.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ExportTaskService service = service(tasks, worker, currentUser());

        ExportTaskDtos.ExportTaskResponse response = service.create(
                ExportReportType.WEEKLY, ExportFormat.PDF, LocalDate.of(2026, 7, 19), null, null, null);

        assertEquals(LocalDate.of(2026, 7, 13), response.periodStart());
        assertEquals(LocalDate.of(2026, 7, 19), response.periodEnd());
    }

    @Test
    void customRangeValidationRejectsBadInput() {
        ExportTaskRepository tasks = mock(ExportTaskRepository.class);
        ExportTaskWorker worker = mock(ExportTaskWorker.class);
        when(tasks.countByUserIdAndStatus(42L, ExportTaskStatus.PENDING)).thenReturn(0L);
        ExportTaskService service = service(tasks, worker, currentUser());

        assertThrows(IllegalArgumentException.class, () -> service.create(ExportReportType.CUSTOM,
                ExportFormat.XLSX, null, null, LocalDate.of(2026, 6, 30), LocalDate.of(2026, 1, 1)));
        assertThrows(IllegalArgumentException.class, () -> service.create(ExportReportType.CUSTOM,
                ExportFormat.XLSX, null, null, LocalDate.of(2026, 1, 1), LocalDate.now().plusDays(1)));
        assertThrows(IllegalArgumentException.class, () -> service.create(ExportReportType.CUSTOM,
                ExportFormat.XLSX, null, null, LocalDate.of(2000, 1, 1), LocalDate.now()));
    }

    @Test
    void createRejectsWhenPendingLimitIsReached() {
        ExportTaskRepository tasks = mock(ExportTaskRepository.class);
        ExportTaskWorker worker = mock(ExportTaskWorker.class);
        when(tasks.countByUserIdAndStatus(42L, ExportTaskStatus.PENDING)).thenReturn(5L);
        ExportTaskService service = service(tasks, worker, currentUser());

        ApiException ex = assertThrows(ApiException.class, () -> service.create(
                ExportReportType.WEEKLY, ExportFormat.XLSX, null, null, null, null));

        assertEquals(42900, ex.errorCode().code());
    }

    @Test
    void getReturnsOnlyOwnedTask() {
        ExportTaskRepository tasks = mock(ExportTaskRepository.class);
        ExportTaskWorker worker = mock(ExportTaskWorker.class);
        User user = new User("demo", "hash");
        when(tasks.findByIdAndUserId(7L, 42L)).thenReturn(Optional.of(task(user)));
        ExportTaskService service = service(tasks, worker, currentUser());

        assertEquals(ExportTaskStatus.PENDING, service.get(7L).status());

        when(tasks.findByIdAndUserId(8L, 42L)).thenReturn(Optional.empty());
        ApiException ex = assertThrows(ApiException.class, () -> service.get(8L));
        assertEquals(40400, ex.errorCode().code());
    }

    @Test
    void fileThrowsWhilePendingAndReturnsBytesWhenSucceeded() {
        ExportTaskRepository tasks = mock(ExportTaskRepository.class);
        ExportTaskWorker worker = mock(ExportTaskWorker.class);
        User user = new User("demo", "hash");
        ExportTask pending = task(user);
        when(tasks.findByIdAndUserId(7L, 42L)).thenReturn(Optional.of(pending));
        ExportTaskService service = service(tasks, worker, currentUser());

        ApiException conflict = assertThrows(ApiException.class, () -> service.file(7L));
        assertEquals(40900, conflict.errorCode().code());

        byte[] bytes = {1, 2, 3};
        pending.succeed(bytes, "life-habit-custom-2026-01-01_2026-06-30.xlsx");
        ExportTaskService.ExportFile file = service.file(7L);
        assertArrayEquals(bytes, file.content());
        assertEquals("life-habit-custom-2026-01-01_2026-06-30.xlsx", file.fileName());
    }

    private ExportTaskService service(ExportTaskRepository tasks, ExportTaskWorker worker, CurrentUser currentUser) {
        return new ExportTaskService(tasks, worker, PROPERTIES, currentUser);
    }

    private CurrentUser currentUser() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(42L);
        CurrentUser currentUser = mock(CurrentUser.class);
        when(currentUser.require()).thenReturn(user);
        return currentUser;
    }

    private ExportTask task(User user) {
        return new ExportTask(user, ExportReportType.CUSTOM, ExportFormat.XLSX,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30));
    }
}
