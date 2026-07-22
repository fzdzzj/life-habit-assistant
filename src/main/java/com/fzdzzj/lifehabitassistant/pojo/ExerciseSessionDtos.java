package com.fzdzzj.lifehabitassistant.pojo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class ExerciseSessionDtos {
    private ExerciseSessionDtos() {
    }

    public record ExerciseSessionRequest(
            @NotNull ExerciseType exerciseType,
            @Size(max = 50, message = "otherName 长度不得超过 50") String otherName,
            @NotNull ExerciseIntensity intensity,
            @Min(value = 1, message = "durationMinutes 不得小于 1") @Max(value = 600, message = "durationMinutes 不得超过 600") int durationMinutes,
            @NotNull LocalDateTime startedAt,
            @DecimalMin(value = "0.0", message = "distanceKm 不得小于 0") BigDecimal distanceKm,
            @Min(value = 0, message = "caloriesKcal 不得小于 0") @Max(value = 10000, message = "caloriesKcal 不得超过 10000") Integer caloriesKcal,
            @Size(max = 500, message = "note 长度不得超过 500") String note) {
    }

    public record ExerciseSessionResponse(Long id, ExerciseType exerciseType, String otherName,
                                          ExerciseIntensity intensity, int durationMinutes,
                                          int moderateEquivalentMinutes, LocalDateTime startedAt,
                                          BigDecimal distanceKm, Integer caloriesKcal, String note) {
    }
}
