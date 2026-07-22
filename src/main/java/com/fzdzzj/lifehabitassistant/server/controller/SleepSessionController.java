package com.fzdzzj.lifehabitassistant.server.controller;

import com.fzdzzj.lifehabitassistant.common.Result;
import com.fzdzzj.lifehabitassistant.pojo.SleepSessionDtos;
import com.fzdzzj.lifehabitassistant.server.service.SleepSessionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/habits/{date}/sleep-sessions")
@Validated
public class SleepSessionController {
    private final SleepSessionService service;

    public SleepSessionController(SleepSessionService service) {
        this.service = service;
    }

    @GetMapping
    public Result<List<SleepSessionDtos.SleepSessionResponse>> list(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @PastOrPresent(message = "date 不得晚于今天") LocalDate date) {
        return Result.success(service.list(date));
    }

    @PostMapping
    public Result<SleepSessionDtos.SleepSessionResponse> create(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @PastOrPresent(message = "date 不得晚于今天") LocalDate date, @Valid @RequestBody SleepSessionDtos.SleepSessionRequest request) {
        return Result.success(service.create(date, request));
    }

    @PutMapping("/{id}")
    public Result<SleepSessionDtos.SleepSessionResponse> update(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @PastOrPresent(message = "date 不得晚于今天") LocalDate date, @PathVariable @Positive(message = "id 必须大于 0") Long id, @Valid @RequestBody SleepSessionDtos.SleepSessionRequest request) {
        return Result.success(service.update(date, id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @PastOrPresent(message = "date 不得晚于今天") LocalDate date, @PathVariable @Positive(message = "id 必须大于 0") Long id) {
        service.delete(date, id);
        return Result.success(null);
    }
}
