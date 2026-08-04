package com.fzdzzj.lifehabitassistant.config;

import com.fzdzzj.lifehabitassistant.pojo.AdviceSource;
import com.fzdzzj.lifehabitassistant.pojo.AiAdviceDtos;
import com.fzdzzj.lifehabitassistant.pojo.AiAdviceType;
import com.fzdzzj.lifehabitassistant.server.service.AiAdviceProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiAdviceCacheTest {
    private static final LocalDate START = LocalDate.of(2026, 8, 3);
    private static final LocalDate END = LocalDate.of(2026, 8, 3);

    @Test
    void putThenGetShouldReturnTheCachedAdvice() {
        AiAdviceCache cache = cache(Duration.ofMinutes(10), 128);
        AiAdviceCache.CachedAdvice advice = advice();

        cache.put(42L, AiAdviceType.ANALYSIS, START, END, "v1", advice);

        Optional<AiAdviceCache.CachedAdvice> hit =
                cache.get(42L, AiAdviceType.ANALYSIS, START, END, "v1");
        assertTrue(hit.isPresent());
        assertEquals(advice.historyId(), hit.orElseThrow().historyId());
        assertEquals(AdviceSource.AI, hit.orElseThrow().source());
    }

    @Test
    void differentPromptVersionShouldNotHit() {
        AiAdviceCache cache = cache(Duration.ofMinutes(10), 128);
        cache.put(42L, AiAdviceType.ANALYSIS, START, END, "v1", advice());

        assertTrue(cache.get(42L, AiAdviceType.ANALYSIS, START, END, "v2").isEmpty());
    }

    @Test
    void expiredEntryShouldBeTreatedAsMissing() throws InterruptedException {
        AiAdviceCache cache = cache(Duration.ofMillis(1), 128);
        cache.put(42L, AiAdviceType.ANALYSIS, START, END, "v1", advice());

        Thread.sleep(5);

        assertTrue(cache.get(42L, AiAdviceType.ANALYSIS, START, END, "v1").isEmpty());
    }

    @Test
    void maxSizeShouldEvictLeastRecentlyUsedEntries() {
        AiAdviceCache cache = cache(Duration.ofMinutes(10), 1);
        cache.put(42L, AiAdviceType.ANALYSIS, START, END, "v1", advice());
        cache.put(7L, AiAdviceType.ANALYSIS, START, END, "v1", advice());

        assertTrue(cache.get(42L, AiAdviceType.ANALYSIS, START, END, "v1").isEmpty());
        assertTrue(cache.get(7L, AiAdviceType.ANALYSIS, START, END, "v1").isPresent());
    }

    @Test
    void evictUserShouldOnlyRemoveThatUsersEntries() {
        AiAdviceCache cache = cache(Duration.ofMinutes(10), 128);
        cache.put(42L, AiAdviceType.ANALYSIS, START, END, "v1", advice());
        cache.put(7L, AiAdviceType.ANALYSIS, START, END, "v1", advice());

        cache.evictUser(42L);

        assertTrue(cache.get(42L, AiAdviceType.ANALYSIS, START, END, "v1").isEmpty());
        assertTrue(cache.get(7L, AiAdviceType.ANALYSIS, START, END, "v1").isPresent());
    }

    @Test
    void differentPeriodShouldNotHit() {
        AiAdviceCache cache = cache(Duration.ofMinutes(10), 128);
        cache.put(42L, AiAdviceType.ANALYSIS, START, END, "v1", advice());

        assertTrue(cache.get(42L, AiAdviceType.WEEKLY_REPORT, START, END, "v1").isEmpty());
        assertFalse(cache.get(42L, AiAdviceType.ANALYSIS, START.minusDays(1), END, "v1").isPresent());
    }

    private AiAdviceCache cache(Duration ttl, int maxSize) {
        return new AiAdviceCache(new AiAdviceProperties(false, "sk-test", "gpt-demo",
                "https://api.openai.com/v1", 3, 30, 30, "v1", ttl, maxSize));
    }

    private AiAdviceCache.CachedAdvice advice() {
        return new AiAdviceCache.CachedAdvice(AdviceSource.AI,
                new AiAdviceDtos.AiAdviceContent("整体稳定", "睡眠略不足",
                        List.of("固定就寝时间"), "每天记录", "继续保持", "仅供健康参考"),
                99L, LocalDateTime.of(2026, 8, 3, 12, 0));
    }
}
