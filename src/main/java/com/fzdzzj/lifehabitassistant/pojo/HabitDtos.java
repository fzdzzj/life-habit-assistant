package com.fzdzzj.lifehabitassistant.pojo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public final class HabitDtos {
    private HabitDtos() {
    }

    public record HabitRequest(
            @NotNull @PastOrPresent(message = "recordDate 不得晚于今天") LocalDate recordDate,
            @Min(value = 1, message = "dietScore 不得小于 1") @Max(value = 5, message = "dietScore 不得超过 5") int dietScore,
            @Min(value = 0, message = "waterMl 不得小于 0") @Max(value = 10000, message = "waterMl 不得超过 10000") int waterMl,
            @Size(max = 500, message = "note 长度不得超过 500") String note) {
    }

    public record HabitResponse(LocalDate recordDate, long sleepMinutes, double sleepHours,
                                int dietScore, int exerciseMinutes, int moderateEquivalentExerciseMinutes,
                                int waterMl, String note, String dailyEvaluation) {
    }
}
