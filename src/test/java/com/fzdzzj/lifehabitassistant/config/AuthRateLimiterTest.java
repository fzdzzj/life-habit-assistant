package com.fzdzzj.lifehabitassistant.config;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthRateLimiterTest {
    private final MutableClock clock = new MutableClock(Instant.parse("2026-08-03T08:00:00Z"));

    @Test
    void registerWindowAllowsUpToLimitThenRejects() {
        AuthRateLimiter limiter = limiter(3, 100, 5, Duration.ofMinutes(15));

        assertTrue(limiter.tryRegister("1.1.1.1"));
        assertTrue(limiter.tryRegister("1.1.1.1"));
        assertTrue(limiter.tryRegister("1.1.1.1"));
        assertFalse(limiter.tryRegister("1.1.1.1"));
    }

    @Test
    void registerWindowFreesSlotsAfterOneMinute() {
        AuthRateLimiter limiter = limiter(2, 100, 5, Duration.ofMinutes(15));

        assertTrue(limiter.tryRegister("1.1.1.1"));
        assertTrue(limiter.tryRegister("1.1.1.1"));
        assertFalse(limiter.tryRegister("1.1.1.1"));

        clock.advance(Duration.ofMinutes(1));
        assertTrue(limiter.tryRegister("1.1.1.1"));
    }

    @Test
    void registerWindowsAreIsolatedPerIp() {
        AuthRateLimiter limiter = limiter(1, 100, 5, Duration.ofMinutes(15));

        assertTrue(limiter.tryRegister("1.1.1.1"));
        assertFalse(limiter.tryRegister("1.1.1.1"));
        assertTrue(limiter.tryRegister("2.2.2.2"));
    }

    @Test
    void loginFailuresLockTheKeyUntilCooldownElapses() {
        AuthRateLimiter limiter = limiter(100, 100, 3, Duration.ofMinutes(15));

        limiter.recordLoginFailure("1.1.1.1", "demo");
        limiter.recordLoginFailure("1.1.1.1", "demo");
        assertTrue(limiter.tryLogin("1.1.1.1", "demo"));

        limiter.recordLoginFailure("1.1.1.1", "demo");
        assertFalse(limiter.tryLogin("1.1.1.1", "demo"));

        clock.advance(Duration.ofMinutes(15));
        assertTrue(limiter.tryLogin("1.1.1.1", "demo"));
    }

    @Test
    void loginSuccessResetsFailureCount() {
        AuthRateLimiter limiter = limiter(100, 100, 3, Duration.ofMinutes(15));

        limiter.recordLoginFailure("1.1.1.1", "demo");
        limiter.recordLoginFailure("1.1.1.1", "demo");
        limiter.recordLoginSuccess("1.1.1.1", "demo");
        limiter.recordLoginFailure("1.1.1.1", "demo");

        assertTrue(limiter.tryLogin("1.1.1.1", "demo"));
    }

    @Test
    void failureLockoutIsIsolatedPerUsername() {
        AuthRateLimiter limiter = limiter(100, 100, 2, Duration.ofMinutes(15));

        limiter.recordLoginFailure("1.1.1.1", "demo");
        limiter.recordLoginFailure("1.1.1.1", "demo");
        assertFalse(limiter.tryLogin("1.1.1.1", "demo"));
        assertTrue(limiter.tryLogin("1.1.1.1", "other"));
    }

    @Test
    void loginPerIpWindowLimitsTotalAttemptsAcrossUsernames() {
        AuthRateLimiter limiter = limiter(100, 2, 100, Duration.ofMinutes(15));

        assertTrue(limiter.tryLogin("1.1.1.1", "u1"));
        assertTrue(limiter.tryLogin("1.1.1.1", "u2"));
        assertFalse(limiter.tryLogin("1.1.1.1", "u3"));
        assertTrue(limiter.tryLogin("2.2.2.2", "u1"));
    }

    @Test
    void resetDropsAllState() {
        AuthRateLimiter limiter = limiter(1, 1, 1, Duration.ofMinutes(15));

        assertTrue(limiter.tryRegister("1.1.1.1"));
        assertFalse(limiter.tryRegister("1.1.1.1"));

        limiter.reset();
        assertTrue(limiter.tryRegister("1.1.1.1"));
    }

    private AuthRateLimiter limiter(int registerPerIp, int loginPerIp, int loginFailures, Duration cooldown) {
        return new AuthRateLimiter(
                new AuthRateLimitProperties(registerPerIp, loginPerIp, loginFailures, cooldown),
                clock);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
