package com.fzdzzj.lifehabitassistant.server.service;

import com.fzdzzj.lifehabitassistant.common.ApiException;
import com.fzdzzj.lifehabitassistant.config.PaginationProperties;
import com.fzdzzj.lifehabitassistant.pojo.AdminDtos;
import com.fzdzzj.lifehabitassistant.pojo.AiQuotaPeriod;
import com.fzdzzj.lifehabitassistant.pojo.ExportTask;
import com.fzdzzj.lifehabitassistant.pojo.ExportTaskStatus;
import com.fzdzzj.lifehabitassistant.pojo.PageResponse;
import com.fzdzzj.lifehabitassistant.pojo.Role;
import com.fzdzzj.lifehabitassistant.pojo.User;
import com.fzdzzj.lifehabitassistant.server.dao.AiQuotaUsageRepository;
import com.fzdzzj.lifehabitassistant.server.dao.ExportTaskRepository;
import com.fzdzzj.lifehabitassistant.server.dao.HabitRecordRepository;
import com.fzdzzj.lifehabitassistant.server.dao.SessionRepository;
import com.fzdzzj.lifehabitassistant.server.dao.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

@Service
public class AdminService {
    private static final int MAX_QUOTA_LIMIT = 100000;

    private final UserRepository users;
    private final HabitRecordRepository habits;
    private final ExportTaskRepository tasks;
    private final AiQuotaUsageRepository quota;
    private final SessionRepository sessions;
    private final AiAdviceProperties aiProperties;
    private final PaginationProperties pagination;

    public AdminService(UserRepository users, HabitRecordRepository habits, ExportTaskRepository tasks,
                        AiQuotaUsageRepository quota, SessionRepository sessions,
                        AiAdviceProperties aiProperties, PaginationProperties pagination) {
        this.users = users;
        this.habits = habits;
        this.tasks = tasks;
        this.quota = quota;
        this.sessions = sessions;
        this.aiProperties = aiProperties;
        this.pagination = pagination;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminDtos.AdminUserResponse> listUsers(String search, int page, int size) {
        Page<User> result = search == null || search.isBlank()
                ? users.findAll(pageRequest(page, size))
                : users.findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                search.trim(), search.trim(), pageRequest(page, size));
        return PageResponse.from(result.map(this::toUserResponse));
    }

    @Transactional(readOnly = true)
    public AdminDtos.AdminUserDetailResponse getUser(Long id) {
        User user = requireUser(id);
        LocalDateTime now = LocalDateTime.now();
        return new AdminDtos.AdminUserDetailResponse(user.getId(), user.getUsername(), user.getEmail(),
                user.getRole(), user.isEnabled(), user.getCreatedAt(),
                habits.countByUserId(id), tasks.countByUserId(id),
                used(user.getId(), AiQuotaPeriod.DAY, now.toLocalDate().toString()),
                dailyLimitOf(user),
                used(user.getId(), AiQuotaPeriod.MONTH, YearMonth.now().toString()),
                monthlyLimitOf(user));
    }

    @Transactional
    public AdminDtos.AdminUserResponse updateUser(Long id, AdminDtos.UpdateUserRequest request) {
        User user = requireUser(id);
        if (request.email() != null) {
            String email = normalizeEmail(request.email());
            if (!java.util.Objects.equals(email, user.getEmail())) {
                if (email != null && users.existsByEmail(email)) {
                    throw ApiException.conflict("邮箱已被使用");
                }
                user.changeEmail(email);
            }
        }
        if (request.role() != null && request.role() != user.getRole()) {
            ensureNotLastAdmin(user, request.role());
            user.changeRole(request.role());
        }
        users.save(user);
        return toUserResponse(user);
    }

    @Transactional
    public AdminDtos.AdminUserResponse disable(Long id) {
        User user = requireUser(id);
        if (user.isEnabled() && user.getRole() == Role.ADMIN
                && users.countByRoleAndEnabled(Role.ADMIN, true) <= 1) {
            throw ApiException.badRequest("系统至少需要保留一名有效管理员");
        }
        user.setEnabled(false);
        users.save(user);
        sessions.revokeAllForUser(user.getId(), LocalDateTime.now());
        return toUserResponse(user);
    }

    @Transactional
    public AdminDtos.AdminUserResponse enable(Long id) {
        User user = requireUser(id);
        user.setEnabled(true);
        users.save(user);
        return toUserResponse(user);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminDtos.QuotaResponse> listQuotas(String search, int page, int size) {
        Page<User> result = search == null || search.isBlank()
                ? users.findAll(pageRequest(page, size))
                : users.findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                search.trim(), search.trim(), pageRequest(page, size));
        return PageResponse.from(result.map(this::toQuotaResponse));
    }

    @Transactional
    public AdminDtos.QuotaResponse updateQuota(Long userId, AdminDtos.UpdateQuotaRequest request) {
        User user = requireUser(userId);
        validateLimit(request.dailyLimit(), "dailyLimit");
        validateLimit(request.monthlyLimit(), "monthlyLimit");
        user.setAiDailyLimit(request.dailyLimit());
        user.setAiMonthlyLimit(request.monthlyLimit());
        users.save(user);
        return toQuotaResponse(user);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminDtos.AdminExportTaskResponse> listExportTasks(String status, Long userId,
                                                                           int page, int size) {
        Page<ExportTask> result;
        if (userId != null) {
            result = tasks.findWithUserByUserId(userId, pageRequest(page, size));
        } else if (status != null) {
            result = tasks.findWithUserByStatus(parseStatus(status), pageRequest(page, size));
        } else {
            result = tasks.findAllWithUser(pageRequest(page, size));
        }
        return PageResponse.from(result.map(this::toExportTaskResponse));
    }

    @Transactional
    public AdminDtos.AdminExportTaskResponse cancelExportTask(Long id) {
        ExportTask task = tasks.findWithUserById(id)
                .orElseThrow(() -> ApiException.notFound("导出任务不存在"));
        if (tasks.markCancelledAnyUser(id, LocalDateTime.now()) != 1) {
            throw ApiException.conflict("任务已结束，无法取消");
        }
        return toExportTaskResponse(tasks.findWithUserById(id).orElse(task));
    }

    @Transactional(readOnly = true)
    public AdminDtos.AdminStatsResponse stats() {
        Map<ExportTaskStatus, Long> byStatus = new EnumMap<>(ExportTaskStatus.class);
        for (ExportTaskStatus status : ExportTaskStatus.values()) {
            byStatus.put(status, tasks.countByStatus(status));
        }
        LocalDateTime now = LocalDateTime.now();
        return new AdminDtos.AdminStatsResponse(
                users.count(),
                users.countByEnabled(true),
                users.countByEnabled(false),
                users.countByRole(Role.ADMIN),
                tasks.count(),
                byStatus,
                quota.sumUsedCount(AiQuotaPeriod.DAY.name(), now.toLocalDate().toString()));
    }

    private AdminDtos.AdminUserResponse toUserResponse(User user) {
        return new AdminDtos.AdminUserResponse(user.getId(), user.getUsername(), user.getEmail(),
                user.getRole(), user.isEnabled(), user.getCreatedAt());
    }

    private AdminDtos.QuotaResponse toQuotaResponse(User user) {
        LocalDateTime now = LocalDateTime.now();
        return new AdminDtos.QuotaResponse(user.getId(), user.getUsername(),
                used(user.getId(), AiQuotaPeriod.DAY, now.toLocalDate().toString()),
                dailyLimitOf(user),
                used(user.getId(), AiQuotaPeriod.MONTH, YearMonth.now().toString()),
                monthlyLimitOf(user));
    }

    private AdminDtos.AdminExportTaskResponse toExportTaskResponse(ExportTask task) {
        return new AdminDtos.AdminExportTaskResponse(task.getId(), task.getUser().getId(),
                task.getUser().getUsername(), task.getReportType(), task.getFormat(),
                task.getPeriodStart(), task.getPeriodEnd(), task.getStatus(), task.getFileName(),
                task.getErrorMessage(), task.getCreatedAt(), task.getStartedAt(), task.getFinishedAt());
    }

    private int used(Long userId, AiQuotaPeriod period, String key) {
        return quota.findUsedCount(userId, period.name(), key).orElse(0);
    }

    private int dailyLimitOf(User user) {
        return user.getAiDailyLimit() == null ? aiProperties.dailyLimit() : user.getAiDailyLimit();
    }

    private int monthlyLimitOf(User user) {
        return user.getAiMonthlyLimit() == null ? aiProperties.monthlyLimit() : user.getAiMonthlyLimit();
    }

    private void ensureNotLastAdmin(User target, Role newRole) {
        if (target.getRole() == Role.ADMIN && target.isEnabled() && newRole != Role.ADMIN
                && users.countByRoleAndEnabled(Role.ADMIN, true) <= 1) {
            throw ApiException.badRequest("系统至少需要保留一名有效管理员");
        }
    }

    private void validateLimit(Integer value, String name) {
        if (value != null && (value < 0 || value > MAX_QUOTA_LIMIT)) {
            throw ApiException.badRequest(name + " 必须在 0 到 " + MAX_QUOTA_LIMIT + " 之间");
        }
    }

    private User requireUser(Long id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("用户不存在"));
    }

    private ExportTaskStatus parseStatus(String status) {
        try {
            return ExportTaskStatus.valueOf(status.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("status 仅支持 "
                    + Arrays.toString(ExportTaskStatus.values()).toLowerCase(Locale.ROOT));
        }
    }

    private PageRequest pageRequest(int page, int size) {
        long offset = (long) page * size;
        if (offset >= pagination.maxOffset()) {
            throw new IllegalArgumentException("页码过深（offset 不得超过 " + pagination.maxOffset() + "）");
        }
        return PageRequest.of(page, size, Sort.by("createdAt").descending());
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
