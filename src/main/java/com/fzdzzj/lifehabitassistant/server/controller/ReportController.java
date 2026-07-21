package com.fzdzzj.lifehabitassistant.server.controller;

import com.fzdzzj.lifehabitassistant.common.Result;
import com.fzdzzj.lifehabitassistant.pojo.ReportDtos;
import com.fzdzzj.lifehabitassistant.server.service.ReportExporter;
import com.fzdzzj.lifehabitassistant.server.service.ReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;

@RestController
@RequestMapping("/api/reports")
@Validated
public class ReportController {
    private final ReportService reports;
    private final ReportExporter exporter;

    public ReportController(ReportService reports, ReportExporter exporter) {
        this.reports = reports;
        this.exporter = exporter;
    }

    @GetMapping("/weekly")
    public Result<ReportDtos.ReportResponse> weekly(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week) {
        return Result.success(reports.weekly(week == null ? LocalDate.now() : week));
    }

    @GetMapping("/monthly")
    public Result<ReportDtos.ReportResponse> monthly(@RequestParam(required = false) String month) {
        return Result.success(reports.monthly(month == null ? YearMonth.now() : parseMonth(month)));
    }

    @GetMapping("/weekly/export")
    public ResponseEntity<byte[]> exportWeekly(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week, @RequestParam String format) {
        return export(reports.weekly(week == null ? LocalDate.now() : week), format, "weekly");
    }

    @GetMapping("/monthly/export")
    public ResponseEntity<byte[]> exportMonthly(@RequestParam(required = false) String month, @RequestParam String format) {
        return export(reports.monthly(month == null ? YearMonth.now() : parseMonth(month)), format, "monthly");
    }

    private ResponseEntity<byte[]> export(ReportDtos.ReportResponse report, String format, String type) {
        boolean xlsx = "xlsx".equalsIgnoreCase(format), pdf = "pdf".equalsIgnoreCase(format);
        if (!xlsx && !pdf) throw new IllegalArgumentException("format 仅支持 xlsx 或 pdf");
        byte[] bytes = xlsx ? exporter.xlsx(report) : exporter.pdf(report);
        String file = "life-habit-" + type + "-report." + (xlsx ? "xlsx" : "pdf");
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file + "\"").contentType(MediaType.parseMediaType(xlsx ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" : "application/pdf")).body(bytes);
    }

    private YearMonth parseMonth(String month) {
        try {
            return YearMonth.parse(month);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("month 必须为 yyyy-MM 格式");
        }
    }
}
