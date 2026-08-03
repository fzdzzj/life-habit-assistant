package com.fzdzzj.lifehabitassistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tightens the in-memory rate limits for this class only; the shared test
 * profile keeps generous defaults so other integration tests are unaffected.
 * Each test uses a unique client IP, so tests never share limiter state.
 */
@SpringBootTest(properties = {
        "app.security.rate-limit.register-per-ip=3",
        "app.security.rate-limit.login-per-ip=100",
        "app.security.rate-limit.login-failures=3",
        "app.security.rate-limit.login-cooldown=15m"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthRateLimitHttpIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void registerShouldBeRejectedOncePerIpLimitIsReached() throws Exception {
        String ip = uniqueIp("10.0");
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/auth/register")
                            .remoteAddress(ip)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(credentials("reg-" + UUID.randomUUID())))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(post("/api/auth/register")
                        .remoteAddress(ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("reg-" + UUID.randomUUID())))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(42900));
    }

    @Test
    void repeatedLoginFailuresShouldLockTheKeyAndRejectCorrectPasswordToo() throws Exception {
        String ip = uniqueIp("10.1");
        String username = "lock-" + UUID.randomUUID();
        register(ip, username);

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .remoteAddress(ip)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(credentials(username, "wrong-password")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(40100));
        }

        mockMvc.perform(post("/api/auth/login")
                        .remoteAddress(ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(username)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(42900));
    }

    @Test
    void normalLoginShouldNotBeAffectedByOtherUsersFailures() throws Exception {
        String ip = uniqueIp("10.2");
        String username = "ok-" + UUID.randomUUID();
        register(ip, username);

        mockMvc.perform(post("/api/auth/login")
                        .remoteAddress(ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty());
    }

    private void register(String ip, String username) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .remoteAddress(ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(username)))
                .andExpect(status().isCreated());
    }

    private String credentials(String username) {
        return credentials(username, "test-password");
    }

    private String credentials(String username, String password) {
        try {
            return objectMapper.writeValueAsString(Map.of("username", username, "password", password));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String uniqueIp(String prefix) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return prefix + "." + random.nextInt(1, 255) + "." + random.nextInt(1, 255);
    }
}
