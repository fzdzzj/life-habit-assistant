package com.fzdzzj.lifehabitassistant.pojo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record Credentials(@NotBlank @Size(min = 3, max = 50) String username,
                              @NotBlank @Size(min = 8, max = 72) String password,
                              @Email String email,
                              @Size(max = 100) String deviceName,
                              @Size(max = 100) String deviceId) {
        public Credentials(String username, String password) {
            this(username, password, null, null, null);
        }
    }

    public record AuthResponse(String token, String tokenType, String username,
                               String refreshToken, Long sessionId) {
        public AuthResponse(String token, String tokenType, String username) {
            this(token, tokenType, username, null, null);
        }
    }

    public record Device(String deviceName, String deviceId, String ipAddress, String userAgent) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record SessionResponse(Long id, String deviceName, String deviceId, String ipAddress,
                                  String userAgent, LocalDateTime createdAt, LocalDateTime lastActiveAt) {
    }

    public record PasswordResetRequest(@NotBlank @Email String email) {
    }

    public record PasswordResetConfirm(@NotBlank String token,
                                       @NotBlank @Size(min = 8, max = 72) String newPassword) {
    }
}
