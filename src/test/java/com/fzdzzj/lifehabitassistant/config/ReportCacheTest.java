package com.fzdzzj.lifehabitassistant.config;

import com.fzdzzj.lifehabitassistant.pojo.AnalysisDtos;
import com.fzdzzj.lifehabitassistant.pojo.DailyGoals;
import com.fzdzzj.lifehabitassistant.pojo.ReportDtos;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportCacheTest {
    private static final DailyGoals GOALS = new DailyGoals(420, 540, 1500, 30, 3);

    @Test
    void getReturnsEmptyForMissingEntry() {
        ReportCache cache = cache(Duration.ofMinutes(10), 128);

        assertTrue(cache.get(1L, "weekly", LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 19)).isEmpty());
    }

    @Test
    void putThenGetReturnsSameInstance() {
        ReportCache cache = cache(Duration.ofMinutes(10), 128);
        ReportDtos.ReportResponse report = report();

        cache.put(1L, "weekly", LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 19), report);

        assertSame(report, cache.get(1L, "weekly", LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 19)).orElseThrow());
    }

    @Test
    void expiredEntryIsRemoved() throws Exception {
        ReportCache cache = cache(Duration.ofMillis(10), 128);
        cache.put(1L, "weekly", LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 19), report());

        Thread.sleep(30);

        assertTrue(cache.get(1L, "weekly", LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 19)).isEmpty());
    }

    @Test
    void evictUserRemovesOnlyThatUsersEntries() {
        ReportCache cache = cache(Duration.ofMinutes(10), 128);
        LocalDate start = LocalDate.of(2026, 7, 13);
        LocalDate end = LocalDate.of(2026, 7, 19);
        cache.put(1L, "weekly", start, end, report());
        cache.put(2L, "weekly", start, end, report());

        cache.evictUser(1L);

        assertTrue(cache.get(1L, "weekly", start, end).isEmpty());
        assertTrue(cache.get(2L, "weekly", start, end).isPresent());
    }

    @Test
    void maxSizeEvictsLeastRecentlyUsedEntry() {
        ReportCache cache = cache(Duration.ofMinutes(10), 2);
        LocalDate week = LocalDate.of(2026, 7, 13);
        cache.put(1L, "weekly", week, week.plusDays(6), report());
        cache.put(1L, "monthly", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), report());

        cache.put(1L, "custom", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30), report());

        assertTrue(cache.get(1L, "weekly", week, week.plusDays(6)).isEmpty());
        assertTrue(cache.get(1L, "monthly", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)).isPresent());
        assertTrue(cache.get(1L, "custom", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30)).isPresent());
    }

    private ReportCache cache(Duration ttl, int maxSize) {
        return new ReportCache(new ReportProperties(ttl, maxSize));
    }

    private ReportDtos.ReportResponse report() {
        var trend = new AnalysisDtos.DailyTrend(LocalDate.of(2026, 7, 13), 7.5, 4, 30, 1600, 0, true);
        return new ReportDtos.ReportResponse("weekly", LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 19),
                1, 7.5, 4, 30, 1600, 0, 100, GOALS, List.of(trend), List.of(), List.of(), List.of(), null);
    }
}
