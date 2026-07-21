package com.fzdzzj.lifehabitassistant.pojo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

public final class HabitDtos {
    private HabitDtos() {
    }

    public record HabitRequest(@NotNull LocalDate recordDate, @NotNull LocalTime bedtime, @NotNull LocalTime wakeTime,
                               @Min(1) @Max(5) int dietScore, @Min(0) @Max(1440) int exerciseMinutes,
                               @Min(0) @Max(20000) int waterMl, @Size(max = 500) String note) {
    }

    public record HabitResponse(LocalDate recordDate, LocalTime bedtime, LocalTime wakeTime, long sleepMinutes,
                                double sleepHours, int dietScore, int exerciseMinutes, int waterMl, String note,
                                String dailyEvaluation) {
    }
}
