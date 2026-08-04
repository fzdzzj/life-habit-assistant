package com.fzdzzj.lifehabitassistant.pojo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "export_tasks")
public class ExportTask {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, length = 10)
    private ExportReportType reportType;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ExportFormat format;
    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;
    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ExportTaskStatus status;
    @Column(name = "file_name", length = 255)
    private String fileName;
    @Lob
    @JdbcTypeCode(SqlTypes.LONGVARBINARY)
    @Column(name = "file_content")
    private byte[] fileContent;
    @Column(name = "error_message", length = 500)
    private String errorMessage;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "started_at")
    private LocalDateTime startedAt;
    @Column(name = "finished_at")
    private LocalDateTime finishedAt;
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    protected ExportTask() {
    }

    public ExportTask(User user, ExportReportType reportType, ExportFormat format,
                      LocalDate periodStart, LocalDate periodEnd) {
        this.user = user;
        this.reportType = reportType;
        this.format = format;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.status = ExportTaskStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    public void succeed(byte[] content, String fileName) {
        this.status = ExportTaskStatus.SUCCEEDED;
        this.fileContent = content;
        this.fileName = fileName;
        this.errorMessage = null;
        this.finishedAt = LocalDateTime.now();
    }

    public void fail(String message) {
        this.status = ExportTaskStatus.FAILED;
        this.errorMessage = message;
        this.finishedAt = LocalDateTime.now();
    }

    public void cancel() {
        this.status = ExportTaskStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public ExportReportType getReportType() {
        return reportType;
    }

    public ExportFormat getFormat() {
        return format;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public ExportTaskStatus getStatus() {
        return status;
    }

    public String getFileName() {
        return fileName;
    }

    public byte[] getFileContent() {
        return fileContent;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }
}
