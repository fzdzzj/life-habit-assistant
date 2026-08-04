package com.fzdzzj.lifehabitassistant.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Bounded single-instance executor for export generation. The worker is small
 * and self-contained; a thread pool is enough and keeps the no-message-queue
 * boundary documented in the optimization plan.
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {
    @Bean(name = "exportTaskExecutor")
    public Executor exportTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("export-");
        executor.initialize();
        return executor;
    }

    /**
     * Bounded executor for streaming AI conversation generation. Streams are
     * long-lived but few; a small pool with a bounded queue keeps them from
     * starving the export workers or blocking request threads.
     */
    @Bean(name = "aiStreamExecutor")
    public Executor aiStreamExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("ai-stream-");
        executor.initialize();
        return executor;
    }
}
