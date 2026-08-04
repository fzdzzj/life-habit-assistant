package com.fzdzzj.lifehabitassistant.config;

import com.fzdzzj.lifehabitassistant.pojo.ReportDtos;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Per-user in-memory report cache with TTL and a bounded size.
 *
 * Reports are immutable DTOs, so storing shared instances is safe. Every
 * data-changing service evicts the affected user after commit so a report
 * never outlives the data it was built from; the TTL only bounds memory for
 * periods the user stops viewing. Low-contention single-instance cache: a
 * synchronized access-order LinkedHashMap is enough and avoids a new
 * dependency (the same boundary documented for the in-memory rate limiter).
 */
@Component
public class ReportCache implements UserDataCache {
    private final Duration ttl;
    private final int maxSize;
    private final Map<String, Entry> entries;

    public ReportCache(ReportProperties properties) {
        this.ttl = properties.cacheTtl();
        this.maxSize = properties.cacheMaxSize();
        this.entries = new LinkedHashMap<>(16, 0.75f, true);
    }

    public synchronized Optional<ReportDtos.ReportResponse> get(Long userId, String type,
                                                                 LocalDate start, LocalDate end) {
        String key = key(userId, type, start, end);
        Entry entry = entries.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.expiresAt().isBefore(Instant.now())) {
            entries.remove(key);
            return Optional.empty();
        }
        return Optional.of(entry.value());
    }

    public synchronized void put(Long userId, String type, LocalDate start, LocalDate end,
                                 ReportDtos.ReportResponse report) {
        entries.put(key(userId, type, start, end), new Entry(report, Instant.now().plus(ttl)));
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

    private String key(Long userId, String type, LocalDate start, LocalDate end) {
        return userId + ":" + type + ":" + start + ":" + end;
    }

    private record Entry(ReportDtos.ReportResponse value, Instant expiresAt) {
    }
}
