package com.fzdzzj.lifehabitassistant.pojo;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_quota_usage", uniqueConstraints = @UniqueConstraint(
        name = "uk_ai_quota_user_period", columnNames = {"user_id", "period_type", "period_key"}))
public class AiQuotaUsage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Enumerated(EnumType.STRING)
    @Column(name = "period_type", nullable = false, length = 10)
    private AiQuotaPeriod periodType;
    @Column(name = "period_key", nullable = false, length = 10)
    private String periodKey;
    @Column(name = "used_count", nullable = false)
    private int usedCount;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected AiQuotaUsage() {
    }

    public AiQuotaUsage(User user, AiQuotaPeriod periodType, String periodKey, LocalDateTime updatedAt) {
        this(user, periodType, periodKey, 0, updatedAt);
    }

    public AiQuotaUsage(User user, AiQuotaPeriod periodType, String periodKey, int usedCount,
                        LocalDateTime updatedAt) {
        this.user = user;
        this.periodType = periodType;
        this.periodKey = periodKey;
        this.usedCount = usedCount;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public AiQuotaPeriod getPeriodType() {
        return periodType;
    }

    public String getPeriodKey() {
        return periodKey;
    }

    public int getUsedCount() {
        return usedCount;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
