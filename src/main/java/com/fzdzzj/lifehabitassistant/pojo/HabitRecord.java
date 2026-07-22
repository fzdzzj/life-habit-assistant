package com.fzdzzj.lifehabitassistant.pojo;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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
    @OneToMany(mappedBy = "habitRecord", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sleepStartAt ASC")
    private final List<SleepSession> sleepSessions = new ArrayList<>();
    @OneToMany(mappedBy = "habitRecord", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("startedAt ASC")
    private final List<ExerciseSession> exerciseSessions = new ArrayList<>();
    @Column(name = "diet_score", nullable = false)
    private int dietScore;
    @Column(name = "water_ml", nullable = false)
    private int waterMl;
    @Column(length = 500)
    private String note;

    protected HabitRecord() {
    }

    public HabitRecord(User user, LocalDate date, int dietScore, int waterMl, String note) {
        this.user = user;
        this.recordDate = date;
        update(dietScore, waterMl, note);
    }

    public void update(int dietScore, int waterMl, String note) {
        this.dietScore = dietScore;
        this.waterMl = waterMl;
        this.note = note;
    }

    public long sleepMinutes() {
        return sleepSessions.stream().mapToLong(session -> java.time.Duration.between(session.getSleepStartAt(), session.getWakeAt()).toMinutes()).sum();
    }

    public void addSleepSession(SleepSession session) {
        sleepSessions.add(session);
    }

    public void addExerciseSession(ExerciseSession session) {
        exerciseSessions.add(session);
    }

    public int exerciseMinutes() {
        return exerciseSessions.stream().mapToInt(ExerciseSession::getDurationMinutes).sum();
    }

    public int moderateEquivalentExerciseMinutes() {
        return exerciseSessions.stream().mapToInt(ExerciseSession::moderateEquivalentMinutes).sum();
    }

    public long strengthExerciseCount() {
        return exerciseSessions.stream().filter(ExerciseSession::isStrengthTraining).count();
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

    public List<SleepSession> getSleepSessions() {
        return List.copyOf(sleepSessions);
    }

    public List<ExerciseSession> getExerciseSessions() {
        return List.copyOf(exerciseSessions);
    }

    public int getDietScore() {
        return dietScore;
    }

    public int getWaterMl() {
        return waterMl;
    }

    public String getNote() {
        return note;
    }
}
