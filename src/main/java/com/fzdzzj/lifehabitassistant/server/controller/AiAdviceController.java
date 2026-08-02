package com.fzdzzj.lifehabitassistant.server.controller;

import com.fzdzzj.lifehabitassistant.common.Result;
import com.fzdzzj.lifehabitassistant.pojo.AiAdviceDtos;
import com.fzdzzj.lifehabitassistant.server.service.AiAdviceService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PastOrPresent;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;

@RestController
@RequestMapping("/api/ai")
@Validated
public class AiAdviceController {
    private final AiAdviceService service;

    public AiAdviceController(AiAdviceService service) {
        this.service = service;
    }

    @PostMapping("/analyses")
    public Result<AiAdviceDtos.AiAdviceResponse> analysis(
            @RequestParam(defaultValue = "7") @Min(1) @Max(366) int days) {
        return Result.success(service.analysis(days));
    }

    @PostMapping("/reports/weekly")
    public Result<AiAdviceDtos.AiAdviceResponse> weekly(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            @PastOrPresent(message = "week 不得晚于今天") LocalDate week) {
        return Result.success(service.weekly(week == null ? LocalDate.now() : week));
    }

    @PostMapping("/reports/monthly")
    public Result<AiAdviceDtos.AiAdviceResponse> monthly(@RequestParam(required = false) String month) {
        return Result.success(service.monthly(month == null ? YearMonth.now() : parseMonth(month)));
    }

    private YearMonth parseMonth(String month) {
        try {
            YearMonth parsed = YearMonth.parse(month);
            if (parsed.isAfter(YearMonth.now())) {
                throw new IllegalArgumentException("month 不得晚于当前月份");
            }
            return parsed;
        } catch (java.time.DateTimeException ex) {
            throw new IllegalArgumentException("month 必须为 yyyy-MM 格式");
        }
    }
}
