package com.fzdzzj.lifehabitassistant.server.service;

import com.fzdzzj.lifehabitassistant.pojo.ExportFormat;
import com.fzdzzj.lifehabitassistant.pojo.ExportTask;
import com.fzdzzj.lifehabitassistant.pojo.ReportDtos;
import com.fzdzzj.lifehabitassistant.server.dao.ExportTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.YearMonth;

/**
 * Generates export files outside the request thread. Runs without a
 * SecurityContext, so every report query goes through the explicit-user
 * overloads captured in the task row at creation time.
 */
@Service
public class ExportTaskWorker {
    private static final Logger log = LoggerFactory.getLogger(ExportTaskWorker.class);
    private static final int MAX_ERROR_LENGTH = 500;

    private final ExportTaskRepository tasks;
    private final ReportService reports;
    private final ReportExporter exporter;
    private final ExportFileStorage storage;

    public ExportTaskWorker(ExportTaskRepository tasks, ReportService reports, ReportExporter exporter,
                            ExportFileStorage storage) {
        this.tasks = tasks;
        this.reports = reports;
        this.exporter = exporter;
        this.storage = storage;
    }

    @Async("exportTaskExecutor")
    public void generate(Long taskId) {
        if (tasks.markRunning(taskId, LocalDateTime.now()) != 1) {
            return;
        }
        ExportTask task = tasks.findWithUserById(taskId).orElse(null);
        if (task == null) {
            return;
        }
        try {
            ReportDtos.ReportResponse report = switch (task.getReportType()) {
                case WEEKLY -> reports.weekly(task.getUser(), task.getPeriodStart());
                case MONTHLY -> reports.monthly(task.getUser(), YearMonth.from(task.getPeriodStart()));
                case CUSTOM -> reports.customForUser(task.getUser(), task.getPeriodStart(), task.getPeriodEnd());
            };
            byte[] bytes = task.getFormat() == ExportFormat.XLSX
                    ? exporter.xlsx(report)
                    : exporter.pdf(report);
            String extension = task.getFormat() == ExportFormat.XLSX ? "xlsx" : "pdf";
            String fileName = "life-habit-" + task.getReportType().name().toLowerCase() + "-"
                    + task.getPeriodStart() + "_" + task.getPeriodEnd() + "." + extension;
            String filePath = ExportFileKeys.key(taskId, fileName);
            storage.store(filePath, bytes);
            int saved = tasks.markSucceeded(taskId, fileName, filePath, LocalDateTime.now());
            if (saved != 1) {
                storage.delete(filePath);
                log.info("Export task {} was cancelled before the file could be persisted", taskId);
            }
        } catch (Exception ex) {
            log.error("Export task {} failed", taskId, ex);
            int saved = tasks.markFailed(taskId, truncate(ex.getMessage()), LocalDateTime.now());
            if (saved != 1) {
                log.info("Export task {} changed state while the worker was failing; result not persisted", taskId);
            }
        }
    }

    private String truncate(String message) {
        if (message == null || message.isBlank()) {
            return "导出失败";
        }
        return message.length() <= MAX_ERROR_LENGTH
                ? message
                : message.substring(0, MAX_ERROR_LENGTH);
    }
}
