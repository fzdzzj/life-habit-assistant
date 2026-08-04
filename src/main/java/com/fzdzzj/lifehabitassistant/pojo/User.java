package com.fzdzzj.lifehabitassistant.pojo;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 50)
    private String username;
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;
    @Column(length = 255)
    private String email;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Role role;
    @Column(nullable = false)
    private boolean enabled;
    @Column(name = "ai_daily_limit")
    private Integer aiDailyLimit;
    @Column(name = "ai_monthly_limit")
    private Integer aiMonthlyLimit;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected User() {
    }

    public User(String username, String passwordHash) {
        this(username, passwordHash, null);
    }

    public User(String username, String passwordHash, String email) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.email = email;
        this.role = Role.USER;
        this.enabled = true;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getEmail() {
        return email;
    }

    public Role getRole() {
        return role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Integer getAiDailyLimit() {
        return aiDailyLimit;
    }

    public Integer getAiMonthlyLimit() {
        return aiMonthlyLimit;
    }

    public void changeEmail(String email) {
        this.email = email;
    }

    public void changeRole(Role role) {
        this.role = role;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setAiDailyLimit(Integer aiDailyLimit) {
        this.aiDailyLimit = aiDailyLimit;
    }

    public void setAiMonthlyLimit(Integer aiMonthlyLimit) {
        this.aiMonthlyLimit = aiMonthlyLimit;
    }

    public void updatePassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
