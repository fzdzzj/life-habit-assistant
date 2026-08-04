package com.fzdzzj.lifehabitassistant.server.service;

import com.fzdzzj.lifehabitassistant.common.ApiException;
import com.fzdzzj.lifehabitassistant.config.ExportProperties;
import com.fzdzzj.lifehabitassistant.pojo.ExportFormat;
import com.fzdzzj.lifehabitassistant.pojo.ExportReportType;
import com.fzdzzj.lifehabitassistant.pojo.ExportTask;
import com.fzdzzj.lifehabitassistant.pojo.ExportTaskDtos;
import com.fzdzzj.lifehabitassistant.pojo.ExportTaskStatus;
import com.fzdzzj.lifehabitassistant.pojo.User;
import com.fzdzzj.lifehabitassistant.server.dao.ExportTaskRepository;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

@Service
public class ExportTaskService {
    private final ExportTaskRepository tasks;
    private final ExportTaskWorker worker;
    private final ExportProperties properties;
    private final CurrentUser currentUser;

    public ExportTaskService(ExportTaskRepository tasks, ExportTaskWorker worker, ExportProperties properties,
                             CurrentUser currentUser) {
        this.tasks = tasks;
        this.worker = worker;
        this.properties = properties;
        this.currentUser = currentUser;
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

    public ExportFile file(Long id) {
        ExportTask task = requireOwned(id);
        if (task.getStatus() == ExportTaskStatus.SUCCEEDED) {
            return new ExportFile(task.getFileName(), task.getFileContent());
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

    public record ExportFile(String fileName, byte[] content) {
    }
}
