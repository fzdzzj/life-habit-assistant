package com.fzdzzj.lifehabitassistant.server.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Environment-backed settings for the explicit OpenAI advice endpoint.
 * Secrets are never logged or committed; they only exist in process memory.
 */
@ConfigurationProperties(prefix = "app.ai.advice")
public record AiAdviceProperties(
        boolean enabled,
        String apiKey,
        String model,
        String baseUrl,
        int dailyLimit,
        int monthlyLimit,
        int timeoutSeconds,
        String promptVersion,
        Duration cacheTtl,
        int cacheMaxSize) {

    public AiAdviceProperties {
        if (dailyLimit < 1) {
            throw new IllegalArgumentException("app.ai.advice.daily-limit must be positive");
        }
        if (monthlyLimit < 1) {
            throw new IllegalArgumentException("app.ai.advice.monthly-limit must be positive");
        }
        if (timeoutSeconds < 1) {
            throw new IllegalArgumentException("app.ai.advice.timeout-seconds must be positive");
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.openai.com/v1";
        }
        if (promptVersion == null || promptVersion.isBlank()) {
            promptVersion = "v1";
        }
        if (cacheTtl == null || cacheTtl.isZero() || cacheTtl.isNegative()) {
            throw new IllegalArgumentException("app.ai.advice.cache-ttl must be positive");
        }
        if (cacheMaxSize < 1) {
            throw new IllegalArgumentException("app.ai.advice.cache-max-size must be positive");
        }
    }
}
