package com.fzdzzj.lifehabitassistant.server.controller;

import com.fzdzzj.lifehabitassistant.common.Result;
import com.fzdzzj.lifehabitassistant.pojo.ExerciseSessionDtos;
import com.fzdzzj.lifehabitassistant.server.service.ExerciseSessionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/habits/{date}/exercise-sessions")
@Validated
public class ExerciseSessionController {
    private final ExerciseSessionService service;

    public ExerciseSessionController(ExerciseSessionService service) {
        this.service = service;
    }

    @GetMapping
    public Result<List<ExerciseSessionDtos.ExerciseSessionResponse>> list(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @PastOrPresent(message = "date 不得晚于今天") LocalDate date) {
        return Result.success(service.list(date));
    }

    @PostMapping
    public Result<ExerciseSessionDtos.ExerciseSessionResponse> create(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @PastOrPresent(message = "date 不得晚于今天") LocalDate date, @Valid @RequestBody ExerciseSessionDtos.ExerciseSessionRequest request) {
        return Result.success(service.create(date, request));
    }

    @PutMapping("/{id}")
    public Result<ExerciseSessionDtos.ExerciseSessionResponse> update(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @PastOrPresent(message = "date 不得晚于今天") LocalDate date, @PathVariable @Positive(message = "id 必须大于 0") Long id, @Valid @RequestBody ExerciseSessionDtos.ExerciseSessionRequest request) {
        return Result.success(service.update(date, id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @PastOrPresent(message = "date 不得晚于今天") LocalDate date, @PathVariable @Positive(message = "id 必须大于 0") Long id) {
        service.delete(date, id);
        return Result.success(null);
    }
}
