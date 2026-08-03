package com.fzdzzj.lifehabitassistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Guards against deep-page scans: offset = page * size is capped so a client
 * cannot force a full-table scan by requesting a huge page number. Values are
 * non-secret tuning parameters, so they live in application.yml.
 */
@ConfigurationProperties(prefix = "app.pagination")
public record PaginationProperties(int maxOffset) {

    public PaginationProperties {
        if (maxOffset < 1) {
            throw new IllegalArgumentException("app.pagination.max-offset must be positive");
        }
    }
}
