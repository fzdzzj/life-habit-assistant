package com.fzdzzj.lifehabitassistant.pojo;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity @Table(name = "users")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 50) private String username;
    @Column(name = "password_hash", nullable = false, length = 100) private String passwordHash;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    protected User() {}
    public User(String username, String passwordHash) { this.username = username; this.passwordHash = passwordHash; this.createdAt = LocalDateTime.now(); }
    public Long getId() { return id; } public String getUsername() { return username; } public String getPasswordHash() { return passwordHash; }
}
