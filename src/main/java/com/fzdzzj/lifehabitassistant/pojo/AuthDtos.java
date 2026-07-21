package com.fzdzzj.lifehabitassistant.pojo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record Credentials(@NotBlank @Size(min = 3, max = 50) String username,
                              @NotBlank @Size(min = 8, max = 72) String password) {
    }

    public record AuthResponse(String token, String tokenType, String username) {
    }
}
