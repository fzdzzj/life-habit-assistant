package com.fzdzzj.lifehabitassistant.server.controller;

import com.fzdzzj.lifehabitassistant.common.Result;
import com.fzdzzj.lifehabitassistant.pojo.ExportFormat;
import com.fzdzzj.lifehabitassistant.pojo.ExportReportType;
import com.fzdzzj.lifehabitassistant.pojo.ExportTaskDtos;
import com.fzdzzj.lifehabitassistant.server.service.ExportTaskService;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Locale;

@RestController
@RequestMapping("/api/export-tasks")
@Validated
public class ExportTaskController {
    private static final String XLSX_MEDIA_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ExportTaskService exports;

    public ExportTaskController(ExportTaskService exports) {
        this.exports = exports;
    }

    @PostMapping
    public ResponseEntity<Result<ExportTaskDtos.ExportTaskResponse>> create(
            @RequestParam @Pattern(regexp = "(?i)weekly|monthly|custom", message = "type 仅支持 weekly、monthly 或 custom") String type,
            @RequestParam @Pattern(regexp = "(?i)xlsx|pdf", message = "format 仅支持 xlsx 或 pdf") String format,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @PastOrPresent(message = "week 不得晚于今天") LocalDate week,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @PastOrPresent(message = "end 不得晚于今天") LocalDate end) {
        ExportTaskDtos.ExportTaskResponse response = exports.create(
                ExportReportType.valueOf(type.toUpperCase(Locale.ROOT)),
                ExportFormat.valueOf(format.toUpperCase(Locale.ROOT)),
                week, parseMonth(month), start, end);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Result.success(response));
    }

    @GetMapping("/{id}")
    public Result<ExportTaskDtos.ExportTaskResponse> get(@PathVariable Long id) {
        return Result.success(exports.get(id));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        ExportTaskService.ExportFile file = exports.file(id);
        boolean xlsx = file.fileName().endsWith(".xlsx");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.fileName() + "\"")
                .contentType(MediaType.parseMediaType(xlsx ? XLSX_MEDIA_TYPE : MediaType.APPLICATION_PDF_VALUE))
                .body(new InputStreamResource(file.content()));
    }

    private YearMonth parseMonth(String month) {
        if (month == null) {
            return null;
        }
        try {
            return YearMonth.parse(month);
        } catch (DateTimeException ex) {
            throw new IllegalArgumentException("month 必须为 yyyy-MM 格式");
        }
    }
}
