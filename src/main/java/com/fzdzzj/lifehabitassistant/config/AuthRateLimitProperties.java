package com.fzdzzj.lifehabitassistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * In-memory rate limits for the public auth endpoints.
 * Values are non-secret tuning parameters, so they live in application.yml.
 */
@ConfigurationProperties(prefix = "app.security.rate-limit")
public record AuthRateLimitProperties(
        int registerPerIp,
        int loginPerIp,
        int loginFailures,
        Duration loginCooldown) {

    public AuthRateLimitProperties {
        if (registerPerIp < 1) {
            throw new IllegalArgumentException("app.security.rate-limit.register-per-ip must be positive");
        }
        if (loginPerIp < 1) {
            throw new IllegalArgumentException("app.security.rate-limit.login-per-ip must be positive");
        }
        if (loginFailures < 1) {
            throw new IllegalArgumentException("app.security.rate-limit.login-failures must be positive");
        }
        if (loginCooldown == null || loginCooldown.isZero() || loginCooldown.isNegative()) {
            throw new IllegalArgumentException("app.security.rate-limit.login-cooldown must be a positive duration");
        }
    }
}
