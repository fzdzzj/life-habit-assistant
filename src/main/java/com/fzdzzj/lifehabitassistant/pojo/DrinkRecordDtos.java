package com.fzdzzj.lifehabitassistant.pojo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class DrinkRecordDtos {
    private DrinkRecordDtos() {
    }

    public record DrinkRecordRequest(
            @NotNull DrinkType drinkType,
            @Size(max = 50, message = "otherName 长度不得超过 50") String otherName,
            @Min(value = 1, message = "volumeMl 不得小于 1") @Max(value = 3000, message = "volumeMl 不得超过 3000") int volumeMl,
            @NotNull LocalDateTime recordedAt,
            @Size(max = 500, message = "note 长度不得超过 500") String note) {
    }

    public record DrinkRecordResponse(Long id, DrinkType drinkType, String otherName, int volumeMl,
                                      int hydrationMl, boolean riskDrink, LocalDateTime recordedAt, String note) {
    }
}
