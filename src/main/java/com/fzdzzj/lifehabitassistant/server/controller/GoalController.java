package com.fzdzzj.lifehabitassistant.server.controller;

import com.fzdzzj.lifehabitassistant.common.Result;
import com.fzdzzj.lifehabitassistant.pojo.DailyGoals;
import com.fzdzzj.lifehabitassistant.pojo.GoalDtos;
import com.fzdzzj.lifehabitassistant.server.service.GoalService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/goals")
public class GoalController {
    private final GoalService service;

    public GoalController(GoalService service) {
        this.service = service;
    }

    @GetMapping
    public Result<DailyGoals> get() {
        return Result.success(service.get());
    }

    @PutMapping
    public Result<DailyGoals> save(@Valid @RequestBody GoalDtos.GoalRequest request) {
        return Result.success(service.save(request));
    }

    @DeleteMapping
    public Result<DailyGoals> reset() {
        return Result.success(service.reset());
    }
}
