package com.fzdzzj.lifehabitassistant.pojo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public final class GoalDtos {
    private GoalDtos() {
    }

    public record GoalRequest(
            @Min(value = 180, message = "minimumSleepMinutes 不得小于 180（3 小时）")
            @Max(value = 720, message = "minimumSleepMinutes 不得超过 720（12 小时）")
            int minimumSleepMinutes,
            @Min(value = 360, message = "maximumSleepMinutes 不得小于 360（6 小时）")
            @Max(value = 960, message = "maximumSleepMinutes 不得超过 960（16 小时）")
            int maximumSleepMinutes,
            @Min(value = 500, message = "minimumHydrationMl 不得小于 500")
            @Max(value = 5000, message = "minimumHydrationMl 不得超过 5000")
            int minimumHydrationMl,
            @Min(value = 0, message = "minimumExerciseMinutes 不得小于 0")
            @Max(value = 600, message = "minimumExerciseMinutes 不得超过 600")
            int minimumExerciseMinutes,
            @Min(value = 1, message = "minimumDietScore 不得小于 1")
            @Max(value = 5, message = "minimumDietScore 不得超过 5")
            int minimumDietScore) {
    }
}
