package com.fzdzzj.lifehabitassistant.server.controller;

import com.fzdzzj.lifehabitassistant.common.Result;
import com.fzdzzj.lifehabitassistant.pojo.AdminDtos;
import com.fzdzzj.lifehabitassistant.pojo.PageResponse;
import com.fzdzzj.lifehabitassistant.server.service.AdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("@accessPolicy.isAdmin()")
@Validated
public class AdminController {
    private final AdminService admin;

    public AdminController(AdminService admin) {
        this.admin = admin;
    }

    @GetMapping("/users")
    Result<PageResponse<AdminDtos.AdminUserResponse>> listUsers(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page 不得小于 0") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size 不得小于 1") @Max(value = 100, message = "size 不得超过 100") int size) {
        return Result.success(admin.listUsers(search, page, size));
    }

    @GetMapping("/users/{id}")
    Result<AdminDtos.AdminUserDetailResponse> getUser(
            @PathVariable @Positive(message = "id 必须大于 0") Long id) {
        return Result.success(admin.getUser(id));
    }

    @PatchMapping("/users/{id}")
    Result<AdminDtos.AdminUserResponse> updateUser(
            @PathVariable @Positive(message = "id 必须大于 0") Long id,
            @Valid @RequestBody AdminDtos.UpdateUserRequest request) {
        return Result.success(admin.updateUser(id, request));
    }

    @PostMapping("/users/{id}/disable")
    Result<AdminDtos.AdminUserResponse> disable(
            @PathVariable @Positive(message = "id 必须大于 0") Long id) {
        return Result.success(admin.disable(id));
    }

    @PostMapping("/users/{id}/enable")
    Result<AdminDtos.AdminUserResponse> enable(
            @PathVariable @Positive(message = "id 必须大于 0") Long id) {
        return Result.success(admin.enable(id));
    }

    @GetMapping("/quotas")
    Result<PageResponse<AdminDtos.QuotaResponse>> listQuotas(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page 不得小于 0") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size 不得小于 1") @Max(value = 100, message = "size 不得超过 100") int size) {
        return Result.success(admin.listQuotas(search, page, size));
    }

    @PatchMapping("/quotas/{userId}")
    Result<AdminDtos.QuotaResponse> updateQuota(
            @PathVariable @Positive(message = "userId 必须大于 0") Long userId,
            @Valid @RequestBody AdminDtos.UpdateQuotaRequest request) {
        return Result.success(admin.updateQuota(userId, request));
    }

    @GetMapping("/export-tasks")
    Result<PageResponse<AdminDtos.AdminExportTaskResponse>> listExportTasks(
            @RequestParam(required = false)
            @Pattern(regexp = "(?i)pending|running|succeeded|failed|cancelled", message = "status 仅支持 pending、running、succeeded、failed 或 cancelled")
            String status,
            @RequestParam(required = false) @Positive(message = "userId 必须大于 0") Long userId,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page 不得小于 0") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size 不得小于 1") @Max(value = 100, message = "size 不得超过 100") int size) {
        return Result.success(admin.listExportTasks(status, userId, page, size));
    }

    @PostMapping("/export-tasks/{id}/cancel")
    Result<AdminDtos.AdminExportTaskResponse> cancelExportTask(
            @PathVariable @Positive(message = "id 必须大于 0") Long id) {
        return Result.success(admin.cancelExportTask(id));
    }

    @GetMapping("/stats")
    Result<AdminDtos.AdminStatsResponse> stats() {
        return Result.success(admin.stats());
    }
}
