package com.fzdzzj.lifehabitassistant.pojo;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class ExportTaskDtos {
    private ExportTaskDtos() {
    }

    public record ExportTaskResponse(Long id, ExportReportType reportType, ExportFormat format,
                                     LocalDate periodStart, LocalDate periodEnd, ExportTaskStatus status,
                                     String fileName, String errorMessage, LocalDateTime createdAt,
                                     LocalDateTime startedAt, LocalDateTime finishedAt) {
    }
}
