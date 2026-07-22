package com.fzdzzj.lifehabitassistant.pojo;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "sleep_sessions")
public class SleepSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "habit_record_id", nullable = false)
    private HabitRecord habitRecord;
    @Enumerated(EnumType.STRING)
    @Column(name = "sleep_type", nullable = false, length = 20)
    private SleepType sleepType;
    @Column(name = "sleep_start_at", nullable = false)
    private LocalDateTime sleepStartAt;
    @Column(name = "wake_at", nullable = false)
    private LocalDateTime wakeAt;

    protected SleepSession() {
    }

    public SleepSession(HabitRecord habitRecord, SleepType sleepType, LocalDateTime sleepStartAt, LocalDateTime wakeAt) {
        this.habitRecord = habitRecord;
        this.sleepType = sleepType;
        this.sleepStartAt = sleepStartAt;
        this.wakeAt = wakeAt;
    }

    public void update(SleepType sleepType, LocalDateTime sleepStartAt, LocalDateTime wakeAt) {
        this.sleepType = sleepType;
        this.sleepStartAt = sleepStartAt;
        this.wakeAt = wakeAt;
    }

    public Long getId() {
        return id;
    }

    public SleepType getSleepType() {
        return sleepType;
    }

    public LocalDateTime getSleepStartAt() {
        return sleepStartAt;
    }

    public LocalDateTime getWakeAt() {
        return wakeAt;
    }
}
