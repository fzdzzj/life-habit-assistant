package com.fzdzzj.lifehabitassistant.server.service;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class ExportTaskService {
    private static final Logger log = LoggerFactory.getLogger(ExportTaskService.class);
    private static final int CLEANUP_BATCH_SIZE = 100;

    private final ExportTaskRepository tasks;
    private final ExportTaskWorker worker;
    private final ExportProperties properties;
    private final PaginationProperties pagination;
    private final CurrentUser currentUser;
    private final ExportFileStorage storage;

    public ExportTaskService(ExportTaskRepository tasks, ExportTaskWorker worker, ExportProperties properties,
                             PaginationProperties pagination, CurrentUser currentUser,
                             ExportFileStorage storage) {
        this.tasks = tasks;
        this.worker = worker;
        this.properties = properties;
        this.pagination = pagination;
        this.currentUser = currentUser;
        this.storage = storage;
    }

    public ExportTaskDtos.ExportTaskResponse create(ExportReportType type, ExportFormat format,
                                                    LocalDate week, YearMonth month,
                                                    LocalDate start, LocalDate end) {
        User user = currentUser.require();
        LocalDate[] period = periodOf(type, week, month, start, end);
        if (tasks.countByUserIdAndStatus(user.getId(), ExportTaskStatus.PENDING) >= properties.maxPendingPerUser()) {
            throw ApiException.tooManyRequests("待处理导出任务过多，请等待现有任务完成后再试");
        }
        ExportTask task = tasks.save(new ExportTask(user, type, format, period[0], period[1]));
        worker.generate(task.getId());
        return toResponse(task);
    }

    public ExportTaskDtos.ExportTaskResponse get(Long id) {
        return toResponse(requireOwned(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<ExportTaskDtos.ExportTaskResponse> list(String status, int page, int size) {
        User user = currentUser.require();
        long offset = (long) page * size;
        if (offset >= pagination.maxOffset()) {
            throw new IllegalArgumentException("页码过深（offset 不得超过 " + pagination.maxOffset() + "）");
        }
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<ExportTask> result = status == null
                ? tasks.findByUserId(user.getId(), pageRequest)
                : tasks.findByUserIdAndStatus(user.getId(), parseStatus(status), pageRequest);
        return PageResponse.from(result.map(this::toResponse));
    }

    @Transactional
    public ExportTaskDtos.ExportTaskResponse cancel(Long id) {
        Long userId = currentUser.require().getId();
        if (tasks.markCancelled(id, userId, LocalDateTime.now()) == 1) {
            return toResponse(requireOwned(id));
        }
        requireOwned(id);
        throw ApiException.conflict("任务已结束，无法取消");
    }

    /**
     * No surrounding transaction: markRetried commits inside the repository
     * call so the async worker submitted below observes the PENDING state
     * (same pattern as create).
     */
    public ExportTaskDtos.ExportTaskResponse retry(Long id) {
        Long userId = currentUser.require().getId();
        if (tasks.countByUserIdAndStatus(userId, ExportTaskStatus.PENDING) >= properties.maxPendingPerUser()) {
            throw ApiException.tooManyRequests("待处理导出任务过多，请等待现有任务完成后再试");
        }
        if (tasks.markRetried(id, userId) != 1) {
            requireOwned(id);
            throw ApiException.conflict("仅失败任务可以重试");
        }
        worker.generate(id);
        return toResponse(requireOwned(id));
    }

    /**
     * Deletes SUCCEEDED tasks older than the configured retention window.
     * Files are removed from external storage first; a task whose file cannot
     * be deleted is kept so the next run can retry. Runs daily and is directly
     * callable from tests.
     */
    @Scheduled(cron = "${app.export.cleanup-cron:0 0 3 * * *}")
    @Transactional
    public void cleanupExpired() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(properties.retentionDays());
        while (true) {
            List<ExportTask> batch = tasks.findByStatusAndCreatedAtBefore(
                    ExportTaskStatus.SUCCEEDED, cutoff, PageRequest.of(0, CLEANUP_BATCH_SIZE));
            if (batch.isEmpty()) {
                return;
            }
            List<Long> expiredIds = new ArrayList<>();
            for (ExportTask task : batch) {
                if (task.getFilePath() != null && !task.getFilePath().isBlank()) {
                    try {
                        storage.delete(task.getFilePath());
                    } catch (RuntimeException ex) {
                        log.error("Keep expired export task id={} because its file could not be deleted",
                                task.getId(), ex);
                        continue;
                    }
                }
                log.info("Cleaning up expired export task id={} fileName={} createdAt={}",
                        task.getId(), task.getFileName(), task.getCreatedAt());
                expiredIds.add(task.getId());
            }
            if (!expiredIds.isEmpty()) {
                tasks.deleteAllByIdInBatch(expiredIds);
            }
        }
    }

    public ExportFile file(Long id) {
        ExportTask task = requireOwned(id);
        if (task.getStatus() == ExportTaskStatus.SUCCEEDED) {
            String filePath = task.getFilePath();
            if (filePath == null || filePath.isBlank()) {
                throw ApiException.notFound("导出文件不存在或已被清理");
            }
            try {
                return new ExportFile(task.getFileName(), storage.load(filePath));
            } catch (ExportFileNotFoundException ex) {
                log.warn("Export file missing task={} path={}", task.getId(), filePath);
                throw ApiException.notFound("导出文件不存在或已被清理");
            } catch (RuntimeException ex) {
                log.error("Failed to read export file task={} path={}", task.getId(), filePath, ex);
                throw ex;
            }
        }
        if (task.getStatus() == ExportTaskStatus.CANCELLED) {
            throw ApiException.conflict("导出任务已取消");
        }
        if (task.getStatus() == ExportTaskStatus.FAILED) {
            String reason = task.getErrorMessage() == null ? "未知错误" : task.getErrorMessage();
            throw ApiException.conflict("导出失败：" + reason);
        }
        throw ApiException.conflict("导出任务尚未完成，请稍后重试");
    }

    private ExportTask requireOwned(Long id) {
        return tasks.findByIdAndUserId(id, currentUser.require().getId())
                .orElseThrow(() -> ApiException.notFound("导出任务不存在"));
    }

    private ExportTaskStatus parseStatus(String status) {
        try {
            return ExportTaskStatus.valueOf(status.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("status 仅支持 "
                    + Arrays.toString(ExportTaskStatus.values()).toLowerCase(Locale.ROOT));
        }
    }

    private LocalDate[] periodOf(ExportReportType type, LocalDate week, YearMonth month,
                                 LocalDate start, LocalDate end) {
        LocalDate startDate;
        LocalDate endDate;
        switch (type) {
            case WEEKLY -> {
                LocalDate anchor = week == null ? LocalDate.now() : week;
                startDate = anchor.with(DayOfWeek.MONDAY);
                endDate = startDate.plusDays(6);
            }
            case MONTHLY -> {
                YearMonth target = month == null ? YearMonth.now() : month;
                if (target.isAfter(YearMonth.now())) {
                    throw new IllegalArgumentException("month 不得晚于当前月份");
                }
                startDate = target.atDay(1);
                endDate = target.atEndOfMonth();
            }
            case CUSTOM -> {
                if (start == null || end == null) {
                    throw new IllegalArgumentException("custom 导出必须提供 start 和 end");
                }
                if (start.isAfter(end)) {
                    throw new IllegalArgumentException("start 不得晚于 end");
                }
                if (end.isAfter(LocalDate.now())) {
                    throw new IllegalArgumentException("end 不得晚于今天");
                }
                long days = ChronoUnit.DAYS.between(start, end) + 1;
                if (days > properties.maxDays()) {
                    throw new IllegalArgumentException("导出区间不得超过 " + properties.maxDays() + " 天");
                }
                startDate = start;
                endDate = end;
            }
            default -> throw new IllegalArgumentException("不支持的导出类型");
        }
        return new LocalDate[]{startDate, endDate};
    }

    private ExportTaskDtos.ExportTaskResponse toResponse(ExportTask task) {
        return new ExportTaskDtos.ExportTaskResponse(task.getId(), task.getReportType(), task.getFormat(),
                task.getPeriodStart(), task.getPeriodEnd(), task.getStatus(), task.getFileName(),
                task.getErrorMessage(), task.getCreatedAt(), task.getStartedAt(), task.getFinishedAt());
    }

    public record ExportFile(String fileName, InputStream content) {
    }
}
