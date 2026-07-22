package com.fzdzzj.lifehabitassistant.pojo;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "exercise_sessions")
public class ExerciseSession {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "habit_record_id", nullable = false)
    private HabitRecord habitRecord;
    @Enumerated(EnumType.STRING) @Column(name = "exercise_type", nullable = false, length = 30)
    private ExerciseType exerciseType;
    @Column(name = "other_name", length = 50)
    private String otherName;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10)
    private ExerciseIntensity intensity;
    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;
    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;
    @Column(name = "distance_km", precision = 6, scale = 2)
    private BigDecimal distanceKm;
    @Column(name = "calories_kcal")
    private Integer caloriesKcal;
    @Column(length = 500)
    private String note;

    protected ExerciseSession() { }

    public ExerciseSession(HabitRecord habitRecord, ExerciseType exerciseType, String otherName, ExerciseIntensity intensity, int durationMinutes, LocalDateTime startedAt, BigDecimal distanceKm, Integer caloriesKcal, String note) {
        this.habitRecord = habitRecord;
        update(exerciseType, otherName, intensity, durationMinutes, startedAt, distanceKm, caloriesKcal, note);
    }

    public void update(ExerciseType exerciseType, String otherName, ExerciseIntensity intensity, int durationMinutes, LocalDateTime startedAt, BigDecimal distanceKm, Integer caloriesKcal, String note) {
        this.exerciseType = exerciseType;
        this.otherName = otherName;
        this.intensity = intensity;
        this.durationMinutes = durationMinutes;
        this.startedAt = startedAt;
        this.distanceKm = distanceKm;
        this.caloriesKcal = caloriesKcal;
        this.note = note;
    }

    public int moderateEquivalentMinutes() { return intensity == ExerciseIntensity.HIGH ? durationMinutes * 2 : durationMinutes; }
    public boolean isStrengthTraining() { return exerciseType == ExerciseType.STRENGTH; }
    public Long getId() { return id; }
    public ExerciseType getExerciseType() { return exerciseType; }
    public String getOtherName() { return otherName; }
    public ExerciseIntensity getIntensity() { return intensity; }
    public int getDurationMinutes() { return durationMinutes; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public BigDecimal getDistanceKm() { return distanceKm; }
    public Integer getCaloriesKcal() { return caloriesKcal; }
    public String getNote() { return note; }
}
