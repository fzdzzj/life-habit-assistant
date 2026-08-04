package com.fzdzzj.lifehabitassistant.config;

import org.springframework.stereotype.Component;

/**
 * Single invalidation point for every user-data-backed cache. Write paths only
 * depend on this coordinator, so adding a new cache later never requires
 * touching the record/goal/export services again.
 */
@Component
public class UserCacheEvictor {
    private final ReportCache reportCache;
    private final AiAdviceCache aiAdviceCache;

    public UserCacheEvictor(ReportCache reportCache, AiAdviceCache aiAdviceCache) {
        this.reportCache = reportCache;
        this.aiAdviceCache = aiAdviceCache;
    }

    /**
     * Clears all user-data caches (reports and AI advice).
     */
    public void evictAll(Long userId) {
        reportCache.evictUser(userId);
        aiAdviceCache.evictUser(userId);
    }

    /**
     * Clears only the report cache; used after a new AI interpretation so the
     * generated result (already cached by {@link AiAdviceCache}) is not wiped.
     */
    public void evictReports(Long userId) {
        reportCache.evictUser(userId);
    }
}
