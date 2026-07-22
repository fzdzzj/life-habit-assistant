package com.fzdzzj.lifehabitassistant.server.controller;

import com.fzdzzj.lifehabitassistant.common.Result;
import com.fzdzzj.lifehabitassistant.pojo.DrinkRecordDtos;
import com.fzdzzj.lifehabitassistant.server.service.DrinkRecordService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/habits/{date}/drink-records")
public class DrinkRecordController {
    private final DrinkRecordService service;

    public DrinkRecordController(DrinkRecordService service) {
        this.service = service;
    }

    @GetMapping
    public Result<List<DrinkRecordDtos.DrinkRecordResponse>> list(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(service.list(date));
    }

    @PostMapping
    public Result<DrinkRecordDtos.DrinkRecordResponse> create(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                               @Valid @RequestBody DrinkRecordDtos.DrinkRecordRequest request) {
        return Result.success(service.create(date, request));
    }

    @PutMapping("/{id}")
    public Result<DrinkRecordDtos.DrinkRecordResponse> update(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                               @PathVariable Long id, @Valid @RequestBody DrinkRecordDtos.DrinkRecordRequest request) {
        return Result.success(service.update(date, id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date, @PathVariable Long id) {
        service.delete(date, id);
        return Result.success(null);
    }
}
