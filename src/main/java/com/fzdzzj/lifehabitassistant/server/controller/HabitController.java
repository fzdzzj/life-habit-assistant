package com.fzdzzj.lifehabitassistant.server.controller;

import com.fzdzzj.lifehabitassistant.common.Result;
import com.fzdzzj.lifehabitassistant.pojo.HabitDtos;
import com.fzdzzj.lifehabitassistant.pojo.PageResponse;
import com.fzdzzj.lifehabitassistant.server.service.HabitService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PastOrPresent;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/habits")
@Validated
public class HabitController {
    private final HabitService service;

    public HabitController(HabitService service) {
        this.service = service;
    }

    @PostMapping
    public Result<HabitDtos.HabitResponse> save(@Valid @RequestBody HabitDtos.HabitRequest request) {
        return Result.success(service.save(request));
    }

    @GetMapping
    public Result<PageResponse<HabitDtos.HabitResponse>> list(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end, @RequestParam(defaultValue = "0") @Min(value = 0, message = "page 不得小于 0") int page, @RequestParam(defaultValue = "20") @Min(value = 1, message = "size 不得小于 1") @Max(value = 100, message = "size 不得超过 100") int size) {
        return Result.success(service.list(start, end, page, size));
    }

    @GetMapping("/{date}")
    public Result<HabitDtos.HabitResponse> get(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @PastOrPresent(message = "date 不得晚于今天") LocalDate date) {
        return Result.success(service.get(date));
    }

    @DeleteMapping("/{date}")
    public Result<Void> delete(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @PastOrPresent(message = "date 不得晚于今天") LocalDate date) {
        service.delete(date);
        return Result.success(null);
    }
}
