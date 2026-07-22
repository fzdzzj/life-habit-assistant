package com.fzdzzj.lifehabitassistant.server.controller;

import com.fzdzzj.lifehabitassistant.common.Result;
import com.fzdzzj.lifehabitassistant.pojo.SleepSessionDtos;
import com.fzdzzj.lifehabitassistant.server.service.SleepSessionService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/habits/{date}/sleep-sessions")
public class SleepSessionController {
    private final SleepSessionService service;

    public SleepSessionController(SleepSessionService service) {
        this.service = service;
    }

    @GetMapping
    public Result<List<SleepSessionDtos.SleepSessionResponse>> list(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(service.list(date));
    }

    @PostMapping
    public Result<SleepSessionDtos.SleepSessionResponse> create(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date, @Valid @RequestBody SleepSessionDtos.SleepSessionRequest request) {
        return Result.success(service.create(date, request));
    }

    @PutMapping("/{id}")
    public Result<SleepSessionDtos.SleepSessionResponse> update(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date, @PathVariable Long id, @Valid @RequestBody SleepSessionDtos.SleepSessionRequest request) {
        return Result.success(service.update(date, id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date, @PathVariable Long id) {
        service.delete(date, id);
        return Result.success(null);
    }
}
