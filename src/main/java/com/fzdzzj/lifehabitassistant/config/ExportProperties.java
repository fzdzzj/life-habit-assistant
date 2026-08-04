package com.fzdzzj.lifehabitassistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bounds for the asynchronous export feature. Non-secret tuning values live in
 * application.yml; maxDays guards the custom range, maxPendingPerUser prevents
 * one user from flooding the single-instance thread pool.
 */
@ConfigurationProperties(prefix = "app.export")
public record ExportProperties(int maxDays, int maxPendingPerUser) {

    public ExportProperties {
        if (maxDays < 1) {
            throw new IllegalArgumentException("app.export.max-days must be positive");
        }
        if (maxPendingPerUser < 1) {
            throw new IllegalArgumentException("app.export.max-pending-per-user must be positive");
        }
    }
}
