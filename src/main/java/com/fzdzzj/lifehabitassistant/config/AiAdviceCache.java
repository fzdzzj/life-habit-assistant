package com.fzdzzj.lifehabitassistant.config;

import com.fzdzzj.lifehabitassistant.pojo.AdviceSource;
import com.fzdzzj.lifehabitassistant.pojo.AiAdviceDtos;
import com.fzdzzj.lifehabitassistant.pojo.AiAdviceType;
import com.fzdzzj.lifehabitassistant.server.service.AiAdviceProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Per-user in-memory cache for same-period AI interpretations.
 *
 * Only successful AI results are cached: a rule fallback must not block the
 * next request from trying the model again. Cache hits skip the model call,
 * quota reservation and a new history row, so repeating a weekly/monthly
 * request costs nothing until the user's data changes (write paths evict
 * through {@link UserCacheEvictor}) or the TTL expires.
 */
@Component
public class AiAdviceCache implements UserDataCache {
    private final Duration ttl;
    private final int maxSize;
    private final Map<String, Entry> entries;

    public AiAdviceCache(AiAdviceProperties properties) {
        this.ttl = properties.cacheTtl();
        this.maxSize = properties.cacheMaxSize();
        this.entries = new LinkedHashMap<>(16, 0.75f, true);
    }

    public synchronized Optional<CachedAdvice> get(Long userId, AiAdviceType type, LocalDate start,
                                                   LocalDate end, String promptVersion) {
        String key = key(userId, type, start, end, promptVersion);
        Entry entry = entries.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.expiresAt().isBefore(Instant.now())) {
            entries.remove(key);
            return Optional.empty();
        }
        return Optional.of(entry.advice());
    }

    public synchronized void put(Long userId, AiAdviceType type, LocalDate start, LocalDate end,
                                 String promptVersion, CachedAdvice advice) {
        entries.put(key(userId, type, start, end, promptVersion), new Entry(advice, Instant.now().plus(ttl)));
        evictExpired();
        while (entries.size() > maxSize) {
            Iterator<String> eldest = entries.keySet().iterator();
            eldest.next();
            eldest.remove();
        }
    }

    @Override
    public synchronized void evictUser(Long userId) {
        String prefix = userId + ":";
        entries.keySet().removeIf(key -> key.startsWith(prefix));
    }

    private void evictExpired() {
        Instant now = Instant.now();
        entries.values().removeIf(entry -> entry.expiresAt().isBefore(now));
    }

    private String key(Long userId, AiAdviceType type, LocalDate start, LocalDate end,
                       String promptVersion) {
        return userId + ":" + type + ":" + start + ":" + end + ":" + promptVersion;
    }

    /**
     * Cacheable part of an AI interpretation; quota snapshot is always read
     * live, so it is intentionally not stored here.
     */
    public record CachedAdvice(AdviceSource source, AiAdviceDtos.AiAdviceContent content,
                               Long historyId, LocalDateTime createdAt) {
    }

    private record Entry(CachedAdvice advice, Instant expiresAt) {
    }
}
