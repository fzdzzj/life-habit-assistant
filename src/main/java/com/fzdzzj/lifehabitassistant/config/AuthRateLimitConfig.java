package com.fzdzzj.lifehabitassistant.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(AuthRateLimitProperties.class)
public class AuthRateLimitConfig {

    /**
     * Shared clock so the rate limiter can be tested with deterministic time.
     */
    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
