package com.fzdzzj.lifehabitassistant;

import com.fzdzzj.lifehabitassistant.common.ApiException;
import com.fzdzzj.lifehabitassistant.config.ExportProperties;
import com.fzdzzj.lifehabitassistant.config.PaginationProperties;
import com.fzdzzj.lifehabitassistant.pojo.ExportFormat;
import com.fzdzzj.lifehabitassistant.pojo.ExportReportType;
import com.fzdzzj.lifehabitassistant.pojo.ExportTask;
import com.fzdzzj.lifehabitassistant.pojo.ExportTaskDtos;
import com.fzdzzj.lifehabitassistant.pojo.ExportTaskStatus;
import com.fzdzzj.lifehabitassistant.pojo.PageResponse;
import com.fzdzzj.lifehabitassistant.pojo.User;
import com.fzdzzj.lifehabitassistant.server.dao.ExportTaskRepository;
import com.fzdzzj.lifehabitassistant.server.service.CurrentUser;
import com.fzdzzj.lifehabitassistant.server.service.ExportTaskService;
import com.fzdzzj.lifehabitassistant.server.service.ExportTaskWorker;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExportTaskServiceTest {
    private static final ExportProperties PROPERTIES = new ExportProperties(1826, 5, 7);
    private static final PaginationProperties PAGINATION = new PaginationProperties(10000);

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

    @Test
    void listReturnsOwnedTasksWithStatusFilter() {
        ExportTaskRepository tasks = mock(ExportTaskRepository.class);
        ExportTaskWorker worker = mock(ExportTaskWorker.class);
        User user = new User("demo", "hash");
        ExportTask failed = task(user);
        failed.fail("boom");
        when(tasks.findByUserIdAndStatus(eq(42L), eq(ExportTaskStatus.FAILED), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(failed)));
        ExportTaskService service = service(tasks, worker, currentUser());

        PageResponse<ExportTaskDtos.ExportTaskResponse> page =
                service.list("FAILED", 0, 20);

        assertEquals(1, page.content().size());
        assertEquals(ExportTaskStatus.FAILED, page.content().getFirst().status());
        verify(tasks, never()).findByUserId(any(), any());
    }

    @Test
    void listRejectsTooDeepPage() {
        ExportTaskRepository tasks = mock(ExportTaskRepository.class);
        ExportTaskService service = service(tasks, mock(ExportTaskWorker.class), currentUser());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.list(null, 1000, 20));

        assertEquals("页码过深（offset 不得超过 10000）", ex.getMessage());
        verify(tasks, never()).findByUserId(any(), any());
    }

    @Test
    void listRejectsUnknownStatus() {
        ExportTaskService service = service(mock(ExportTaskRepository.class), mock(ExportTaskWorker.class), currentUser());

        assertThrows(IllegalArgumentException.class, () -> service.list("BOGUS", 0, 20));
    }

    @Test
    void cancelMarksPendingOrRunningTaskCancelled() {
        ExportTaskRepository tasks = mock(ExportTaskRepository.class);
        ExportTaskWorker worker = mock(ExportTaskWorker.class);
        User user = new User("demo", "hash");
        ExportTask task = task(user);
        task.cancel();
        when(tasks.markCancelled(eq(7L), eq(42L), any())).thenReturn(1);
        when(tasks.findByIdAndUserId(7L, 42L)).thenReturn(Optional.of(task));
        ExportTaskService service = service(tasks, worker, currentUser());

        ExportTaskDtos.ExportTaskResponse response = service.cancel(7L);

        assertEquals(ExportTaskStatus.CANCELLED, response.status());
    }

    @Test
    void cancelRejectsFinishedTask() {
        ExportTaskRepository tasks = mock(ExportTaskRepository.class);
        User user = new User("demo", "hash");
        ExportTask task = task(user);
        task.succeed(new byte[]{1}, "file.xlsx");
        when(tasks.markCancelled(eq(7L), eq(42L), any())).thenReturn(0);
        when(tasks.findByIdAndUserId(7L, 42L)).thenReturn(Optional.of(task));
        ExportTaskService service = service(tasks, mock(ExportTaskWorker.class), currentUser());

        ApiException ex = assertThrows(ApiException.class, () -> service.cancel(7L));

        assertEquals(40900, ex.errorCode().code());
    }

    @Test
    void cancelReturnsNotFoundForOtherUsersTask() {
        ExportTaskRepository tasks = mock(ExportTaskRepository.class);
        when(tasks.markCancelled(eq(7L), eq(42L), any())).thenReturn(0);
        when(tasks.findByIdAndUserId(7L, 42L)).thenReturn(Optional.empty());
        ExportTaskService service = service(tasks, mock(ExportTaskWorker.class), currentUser());

        ApiException ex = assertThrows(ApiException.class, () -> service.cancel(7L));

        assertEquals(40400, ex.errorCode().code());
    }

    @Test
    void retryMovesFailedTaskBackToPendingAndSubmitsWorker() {
        ExportTaskRepository tasks = mock(ExportTaskRepository.class);
        ExportTaskWorker worker = mock(ExportTaskWorker.class);
        User user = new User("demo", "hash");
        ExportTask task = task(user);
        when(tasks.countByUserIdAndStatus(42L, ExportTaskStatus.PENDING)).thenReturn(0L);
        when(tasks.markRetried(7L, 42L)).thenReturn(1);
        when(tasks.findByIdAndUserId(7L, 42L)).thenReturn(Optional.of(task));
        ExportTaskService service = service(tasks, worker, currentUser());

        ExportTaskDtos.ExportTaskResponse response = service.retry(7L);

        assertEquals(ExportTaskStatus.PENDING, response.status());
        verify(worker).generate(7L);
    }

    @Test
    void retryRejectsNonFailedTask() {
        ExportTaskRepository tasks = mock(ExportTaskRepository.class);
        User user = new User("demo", "hash");
        ExportTask task = task(user);
        task.succeed(new byte[]{1}, "file.xlsx");
        when(tasks.countByUserIdAndStatus(42L, ExportTaskStatus.PENDING)).thenReturn(0L);
        when(tasks.markRetried(7L, 42L)).thenReturn(0);
        when(tasks.findByIdAndUserId(7L, 42L)).thenReturn(Optional.of(task));
        ExportTaskService service = service(tasks, mock(ExportTaskWorker.class), currentUser());

        ApiException ex = assertThrows(ApiException.class, () -> service.retry(7L));

        assertEquals(40900, ex.errorCode().code());
    }

    @Test
    void retryRejectsWhenPendingLimitIsReached() {
        ExportTaskRepository tasks = mock(ExportTaskRepository.class);
        when(tasks.countByUserIdAndStatus(42L, ExportTaskStatus.PENDING)).thenReturn(5L);
        ExportTaskService service = service(tasks, mock(ExportTaskWorker.class), currentUser());

        ApiException ex = assertThrows(ApiException.class, () -> service.retry(7L));

        assertEquals(42900, ex.errorCode().code());
        verify(tasks, never()).markRetried(anyLong(), anyLong());
    }

    @Test
    void retryReturnsNotFoundForOtherUsersTask() {
        ExportTaskRepository tasks = mock(ExportTaskRepository.class);
        when(tasks.countByUserIdAndStatus(42L, ExportTaskStatus.PENDING)).thenReturn(0L);
        when(tasks.markRetried(7L, 42L)).thenReturn(0);
        when(tasks.findByIdAndUserId(7L, 42L)).thenReturn(Optional.empty());
        ExportTaskService service = service(tasks, mock(ExportTaskWorker.class), currentUser());

        ApiException ex = assertThrows(ApiException.class, () -> service.retry(7L));

        assertEquals(40400, ex.errorCode().code());
    }

    @Test
    void cleanupDeletesOnlyExpiredSucceededTasks() {
        ExportTaskRepository tasks = mock(ExportTaskRepository.class);
        User user = new User("demo", "hash");
        ExportTask expired = task(user);
        expired.succeed(new byte[]{1}, "old.xlsx");
        when(tasks.findByStatusAndCreatedAtBefore(eq(ExportTaskStatus.SUCCEEDED), any(), any()))
                .thenReturn(List.of(expired), List.of());
        ExportTaskService service = service(tasks, mock(ExportTaskWorker.class), currentUser());

        service.cleanupExpired();

        verify(tasks).deleteAllByIdInBatch(any());
        verify(tasks, times(2)).findByStatusAndCreatedAtBefore(eq(ExportTaskStatus.SUCCEEDED), any(), any());
    }

    private ExportTaskService service(ExportTaskRepository tasks, ExportTaskWorker worker, CurrentUser currentUser) {
        return new ExportTaskService(tasks, worker, PROPERTIES, PAGINATION, currentUser);
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
