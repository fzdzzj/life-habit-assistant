package com.fzdzzj.lifehabitassistant.config;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class UserCacheEvictorTest {
    @Test
    void evictAllShouldClearReportAndAdviceCaches() {
        ReportCache reports = mock(ReportCache.class);
        AiAdviceCache advice = mock(AiAdviceCache.class);
        UserCacheEvictor evictor = new UserCacheEvictor(reports, advice);

        evictor.evictAll(42L);

        verify(reports).evictUser(42L);
        verify(advice).evictUser(42L);
    }

    @Test
    void evictReportsShouldOnlyClearReportCache() {
        ReportCache reports = mock(ReportCache.class);
        AiAdviceCache advice = mock(AiAdviceCache.class);
        UserCacheEvictor evictor = new UserCacheEvictor(reports, advice);

        evictor.evictReports(42L);

        verify(reports).evictUser(42L);
        verify(advice, never()).evictUser(42L);
    }
}
