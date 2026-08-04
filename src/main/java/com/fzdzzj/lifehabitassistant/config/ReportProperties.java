package com.fzdzzj.lifehabitassistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Tuning knobs for the in-memory report cache. Values are non-secret and live
 * in application.yml; the cache is a memory-bound backstop, while every data
 * write evicts the affected user so reports never go stale for the full TTL.
 */
@ConfigurationProperties(prefix = "app.report")
public record ReportProperties(Duration cacheTtl, int cacheMaxSize) {

    public ReportProperties {
        if (cacheTtl == null || cacheTtl.isZero() || cacheTtl.isNegative()) {
            throw new IllegalArgumentException("app.report.cache-ttl must be positive");
        }
        if (cacheMaxSize < 1) {
            throw new IllegalArgumentException("app.report.cache-max-size must be positive");
        }
    }
}
