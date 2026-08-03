package com.fzdzzj.lifehabitassistant.config;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory rate limiter for the public auth endpoints.
 *
 * <p>Two independent guards:
 * <ul>
 *   <li>sliding-window request quota per IP (register and login);</li>
 *   <li>consecutive-failure lockout per (IP, username) for login.</li>
 * </ul>
 *
 * <p>A {@link Clock} is injected so tests can advance time deterministically.
 * State lives only in this JVM instance; a shared deployment would need a
 * distributed limiter (out of scope by design).
 */
@Component
public class AuthRateLimiter {
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final AuthRateLimitProperties properties;
    private final Clock clock;
    private final ConcurrentHashMap<String, Deque<Instant>> windows = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, FailureState> failures = new ConcurrentHashMap<>();

    public AuthRateLimiter(AuthRateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /** True if the IP still has register quota in the current window. */
    public boolean tryRegister(String ip) {
        return tryAcquire("register:" + ip, properties.registerPerIp());
    }

    /** True if the key is neither failure-locked nor over the per-IP login quota. */
    public boolean tryLogin(String ip, String username) {
        if (isBlocked(ip, username)) {
            return false;
        }
        return tryAcquire("login:" + ip, properties.loginPerIp());
    }

    /** Counts one failed login; reaching the threshold starts the cooldown. */
    public void recordLoginFailure(String ip, String username) {
        failures.compute(failureKey(ip, username), (ignored, state) -> {
            FailureState current = state == null ? new FailureState() : state;
            current.failures++;
            if (current.failures >= properties.loginFailures()) {
                current.blockedUntil = clock.instant().plus(properties.loginCooldown());
                current.failures = 0;
            }
            return current;
        });
    }

    /** Clears failure state after a successful login. */
    public void recordLoginSuccess(String ip, String username) {
        failures.remove(failureKey(ip, username));
    }

    /** Test hook: drop all in-memory state. */
    public void reset() {
        windows.clear();
        failures.clear();
    }

    private boolean isBlocked(String ip, String username) {
        FailureState state = failures.get(failureKey(ip, username));
        return state != null && state.blockedUntil != null && clock.instant().isBefore(state.blockedUntil);
    }

    private boolean tryAcquire(String key, int limit) {
        Instant now = clock.instant();
        boolean[] accepted = {false};
        Deque<Instant> deque = windows.compute(key, (ignored, existing) -> {
            Deque<Instant> current = existing == null ? new ArrayDeque<>() : existing;
            Instant cutoff = now.minus(WINDOW);
            while (!current.isEmpty() && !current.peekFirst().isAfter(cutoff)) {
                current.pollFirst();
            }
            if (current.size() < limit) {
                current.addLast(now);
                accepted[0] = true;
            }
            return current;
        });
        if (deque.isEmpty()) {
            windows.remove(key, deque);
        }
        return accepted[0];
    }

    private static String failureKey(String ip, String username) {
        return ip + "|" + username;
    }

    private static final class FailureState {
        int failures;
        Instant blockedUntil;
    }
}
