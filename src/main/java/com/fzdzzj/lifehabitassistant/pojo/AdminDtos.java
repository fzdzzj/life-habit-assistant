package com.fzdzzj.lifehabitassistant.pojo;

import jakarta.validation.constraints.Email;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

public final class AdminDtos {
    private AdminDtos() {
    }

    public record AdminUserResponse(Long id, String username, String email, Role role,
                                    boolean enabled, LocalDateTime createdAt) {
    }

    public record AdminUserDetailResponse(Long id, String username, String email, Role role,
                                          boolean enabled, LocalDateTime createdAt, long habitRecords,
                                          long exportTasks, int dailyUsed, int dailyLimit,
                                          int monthlyUsed, int monthlyLimit) {
    }

    public record UpdateUserRequest(Role role, @Email String email) {
    }

    public record QuotaResponse(Long userId, String username, int dailyUsed, int dailyLimit,
                                int monthlyUsed, int monthlyLimit) {
    }

    public record UpdateQuotaRequest(Integer dailyLimit, Integer monthlyLimit) {
    }

    public record AdminExportTaskResponse(Long id, Long userId, String username,
                                          ExportReportType reportType, ExportFormat format,
                                          LocalDate periodStart, LocalDate periodEnd,
                                          ExportTaskStatus status, String fileName, String errorMessage,
                                          LocalDateTime createdAt, LocalDateTime startedAt,
                                          LocalDateTime finishedAt) {
    }

    public record AdminStatsResponse(long totalUsers, long activeUsers, long disabledUsers,
                                     long adminUsers, long totalExportTasks,
                                     Map<ExportTaskStatus, Long> exportTasksByStatus, long todayAiCalls) {
    }
}
