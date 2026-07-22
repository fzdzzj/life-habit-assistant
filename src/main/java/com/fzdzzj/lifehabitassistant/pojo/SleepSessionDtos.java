package com.fzdzzj.lifehabitassistant.pojo;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public final class SleepSessionDtos {
    private SleepSessionDtos() {
    }

    public record SleepSessionRequest(@NotNull(message = "sleepType 不得为空") SleepType sleepType,
                                      @NotNull(message = "sleepStartAt 不得为空") LocalDateTime sleepStartAt,
                                      @NotNull(message = "wakeAt 不得为空") LocalDateTime wakeAt) {
    }

    public record SleepSessionResponse(Long id, SleepType sleepType, LocalDateTime sleepStartAt, LocalDateTime wakeAt) {
    }
}
