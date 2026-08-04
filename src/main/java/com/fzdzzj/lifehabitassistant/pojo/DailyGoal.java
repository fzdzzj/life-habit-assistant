package com.fzdzzj.lifehabitassistant.pojo;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "daily_goals", uniqueConstraints = @UniqueConstraint(name = "uk_daily_goal_user", columnNames = "user_id"))
public class DailyGoal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(name = "minimum_sleep_minutes", nullable = false)
    private int minimumSleepMinutes;
    @Column(name = "maximum_sleep_minutes", nullable = false)
    private int maximumSleepMinutes;
    @Column(name = "minimum_hydration_ml", nullable = false)
    private int minimumHydrationMl;
    @Column(name = "minimum_exercise_minutes", nullable = false)
    private int minimumExerciseMinutes;
    @Column(name = "minimum_diet_score", nullable = false)
    private int minimumDietScore;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected DailyGoal() {
    }

    public DailyGoal(User user, DailyGoals goals) {
        this.user = user;
        update(goals);
    }

    public void update(DailyGoals goals) {
        this.minimumSleepMinutes = goals.minimumSleepMinutes();
        this.maximumSleepMinutes = goals.maximumSleepMinutes();
        this.minimumHydrationMl = goals.minimumHydrationMl();
        this.minimumExerciseMinutes = goals.minimumExerciseMinutes();
        this.minimumDietScore = goals.minimumDietScore();
        this.updatedAt = LocalDateTime.now();
    }

    public DailyGoals toGoals() {
        return new DailyGoals(minimumSleepMinutes, maximumSleepMinutes, minimumHydrationMl,
                minimumExerciseMinutes, minimumDietScore);
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
