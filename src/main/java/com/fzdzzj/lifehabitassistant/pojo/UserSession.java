package com.fzdzzj.lifehabitassistant.pojo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "sessions")
public class UserSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(name = "device_name", length = 100)
    private String deviceName;
    @Column(name = "device_id", length = 100)
    private String deviceId;
    @Column(name = "ip_address", length = 45)
    private String ipAddress;
    @Column(name = "user_agent", length = 255)
    private String userAgent;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "last_active_at", nullable = false)
    private LocalDateTime lastActiveAt;
    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    protected UserSession() {
    }

    public UserSession(User user, String deviceName, String deviceId, String ipAddress,
                       String userAgent, LocalDateTime now) {
        this.user = user;
        this.deviceName = deviceName;
        this.deviceId = deviceId;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.createdAt = now;
        this.lastActiveAt = now;
    }

    public void touch(LocalDateTime now) {
        this.lastActiveAt = now;
    }

    public void revoke(LocalDateTime now) {
        this.revokedAt = now;
    }

    public boolean isActive() {
        return revokedAt == null;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getLastActiveAt() {
        return lastActiveAt;
    }

    public LocalDateTime getRevokedAt() {
        return revokedAt;
    }
}
