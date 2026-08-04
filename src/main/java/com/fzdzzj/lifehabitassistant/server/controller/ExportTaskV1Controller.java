package com.fzdzzj.lifehabitassistant.server.controller;

import com.fzdzzj.lifehabitassistant.common.Result;
import com.fzdzzj.lifehabitassistant.pojo.ExportTaskDtos;
import com.fzdzzj.lifehabitassistant.pojo.PageResponse;
import com.fzdzzj.lifehabitassistant.server.service.ExportTaskService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/export-tasks")
@Validated
public class ExportTaskV1Controller {
    private final ExportTaskService exports;

    public ExportTaskV1Controller(ExportTaskService exports) {
        this.exports = exports;
    }

    @GetMapping
    public Result<PageResponse<ExportTaskDtos.ExportTaskResponse>> list(
            @RequestParam(required = false)
            @Pattern(regexp = "(?i)pending|running|succeeded|failed|cancelled", message = "status 仅支持 pending、running、succeeded、failed 或 cancelled")
            String status,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page 不得小于 0") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size 不得小于 1") @Max(value = 100, message = "size 不得超过 100") int size) {
        return Result.success(exports.list(status, page, size));
    }

    @PostMapping("/{id}/cancel")
    public Result<ExportTaskDtos.ExportTaskResponse> cancel(@PathVariable @Positive(message = "id 必须大于 0") Long id) {
        return Result.success(exports.cancel(id));
    }

    @PostMapping("/{id}/retry")
    public Result<ExportTaskDtos.ExportTaskResponse> retry(@PathVariable @Positive(message = "id 必须大于 0") Long id) {
        return Result.success(exports.retry(id));
    }
}
