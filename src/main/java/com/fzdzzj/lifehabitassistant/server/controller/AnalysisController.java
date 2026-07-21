package com.fzdzzj.lifehabitassistant.server.controller;

import com.fzdzzj.lifehabitassistant.common.Result;
import com.fzdzzj.lifehabitassistant.pojo.AnalysisDtos;
import com.fzdzzj.lifehabitassistant.server.service.AnalysisService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@Validated
public class AnalysisController {
    private final AnalysisService service;

    public AnalysisController(AnalysisService service) {
        this.service = service;
    }

    @GetMapping("/trends")
    public Result<AnalysisDtos.TrendResponse> trends(@RequestParam(defaultValue = "7") @Min(1) @Max(366) int days) {
        return Result.success(service.trend(days));
    }

    @PostMapping("/analyses")
    public Result<AnalysisDtos.AnalysisResponse> analysis(@RequestParam(defaultValue = "7") @Min(1) @Max(366) int days) {
        return Result.success(service.analysis(days));
    }
}
