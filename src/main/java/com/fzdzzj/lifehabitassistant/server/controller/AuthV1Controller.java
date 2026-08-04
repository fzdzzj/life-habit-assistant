package com.fzdzzj.lifehabitassistant.server.controller;

import com.fzdzzj.lifehabitassistant.common.Result;
import com.fzdzzj.lifehabitassistant.pojo.AuthDtos;
import com.fzdzzj.lifehabitassistant.server.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auth")
@Validated
public class AuthV1Controller {
    private final AuthService service;

    public AuthV1Controller(AuthService service) {
        this.service = service;
    }

    @PostMapping("/refresh")
    Result<AuthDtos.AuthResponse> refresh(@Valid @RequestBody AuthDtos.RefreshRequest input) {
        return Result.success(service.refresh(input));
    }

    @PostMapping("/logout")
    Result<Void> logout(@Valid @RequestBody AuthDtos.RefreshRequest input) {
        service.logout(input);
        return Result.success(null);
    }

    @GetMapping("/sessions")
    Result<List<AuthDtos.SessionResponse>> sessions() {
        return Result.success(service.sessions());
    }

    @DeleteMapping("/sessions/{id}")
    Result<AuthDtos.SessionResponse> revokeSession(
            @PathVariable @Positive(message = "id 必须大于 0") Long id) {
        return Result.success(service.revokeSession(id));
    }

    @PostMapping("/password-reset/request")
    Result<Void> requestPasswordReset(@Valid @RequestBody AuthDtos.PasswordResetRequest input) {
        service.requestPasswordReset(input.email());
        return Result.success(null);
    }

    @PostMapping("/password-reset/confirm")
    Result<Void> confirmPasswordReset(@Valid @RequestBody AuthDtos.PasswordResetConfirm input) {
        service.confirmPasswordReset(input.token(), input.newPassword());
        return Result.success(null);
    }
}
