package com.fzdzzj.lifehabitassistant.pojo;

import jakarta.persistence.*;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "habit_records", uniqueConstraints = @UniqueConstraint(name = "uk_habit_user_date", columnNames = {"user_id", "record_date"}))
public class HabitRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(name = "record_date", nullable = false)
    private LocalDate recordDate;
    @Column(nullable = false)
    private LocalTime bedtime;
    @Column(name = "wake_time", nullable = false)
    private LocalTime wakeTime;
    @Column(name = "diet_score", nullable = false)
    private int dietScore;
    @Column(name = "exercise_minutes", nullable = false)
    private int exerciseMinutes;
    @Column(name = "water_ml", nullable = false)
    private int waterMl;
    @Column(length = 500)
    private String note;

    protected HabitRecord() {
    }

    public HabitRecord(User user, LocalDate date, LocalTime bedtime, LocalTime wakeTime, int dietScore, int exerciseMinutes, int waterMl, String note) {
        this.user = user;
        this.recordDate = date;
        update(bedtime, wakeTime, dietScore, exerciseMinutes, waterMl, note);
    }

    public void update(LocalTime bedtime, LocalTime wakeTime, int dietScore, int exerciseMinutes, int waterMl, String note) {
        this.bedtime = bedtime;
        this.wakeTime = wakeTime;
        this.dietScore = dietScore;
        this.exerciseMinutes = exerciseMinutes;
        this.waterMl = waterMl;
        this.note = note;
    }

    public long sleepMinutes() {
        long minutes = Duration.between(bedtime, wakeTime).toMinutes();
        return minutes <= 0 ? minutes + 24 * 60 : minutes;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public LocalDate getRecordDate() {
        return recordDate;
    }

    public LocalTime getBedtime() {
        return bedtime;
    }

    public LocalTime getWakeTime() {
        return wakeTime;
    }

    public int getDietScore() {
        return dietScore;
    }

    public int getExerciseMinutes() {
        return exerciseMinutes;
    }

    public int getWaterMl() {
        return waterMl;
    }

    public String getNote() {
        return note;
    }
}
