package com.fzdzzj.lifehabitassistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fzdzzj.lifehabitassistant.pojo.User;
import com.fzdzzj.lifehabitassistant.server.dao.UserRepository;
import com.fzdzzj.lifehabitassistant.server.service.PasswordResetMailService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthV1HttpIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository users;

    @MockitoBean
    private PasswordResetMailService mailService;

    @Test
    void registerShouldReturnAccessRefreshAndSessionId() throws Exception {
        AuthSession session = register("authpair-" + UUID.randomUUID(), "browser", "dev-a",
                "pair@example.com");

        assertNotNull(session.token());
        assertNotNull(session.refreshToken());
        assertNotNull(session.sessionId());
    }

    @Test
    void refreshShouldRotateAndReuseShouldRevokeWholeSession() throws Exception {
        AuthSession session = register("rotate-" + UUID.randomUUID(), "browser", "dev-a", null);
        String oldRefresh = session.refreshToken();

        MvcResult rotated = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", oldRefresh))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andReturn();
        String newRefresh = objectMapper.readTree(rotated.getResponse().getContentAsString())
                .path("data").path("refreshToken").asText();
        assertNotEquals(oldRefresh, newRefresh);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", oldRefresh))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));

        // Reuse of the old token revokes the whole session, including the new pair.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", newRefresh))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    void refreshShouldRejectUnknownToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", "bogus"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    void logoutShouldInvalidateRefreshAndStayIdempotent() throws Exception {
        AuthSession session = register("logout-" + UUID.randomUUID(), "browser", "dev-a", null);

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", session.refreshToken()))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", session.refreshToken()))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", session.refreshToken()))))
                .andExpect(status().isOk());
    }

    @Test
    void sessionsShouldBeMultiDeviceAndRevokeOnlyTarget() throws Exception {
        String username = "multi-" + UUID.randomUUID();
        AuthSession deviceA = register(username, "browser", "dev-a", null);
        AuthSession deviceB = login(username, "dev-b");

        mockMvc.perform(get("/api/v1/auth/sessions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + deviceA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        mockMvc.perform(delete("/api/v1/auth/sessions/{id}", deviceA.sessionId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + deviceA.token()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/auth/sessions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + deviceA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", deviceA.refreshToken()))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", deviceB.refreshToken()))))
                .andExpect(status().isOk());
    }

    @Test
    void revokeOtherUsersSessionShouldReturnNotFound() throws Exception {
        AuthSession owner = register("own-" + UUID.randomUUID(), "browser", "dev-a", null);
        AuthSession other = register("other-" + UUID.randomUUID(), "browser", "dev-b", null);

        mockMvc.perform(delete("/api/v1/auth/sessions/{id}", owner.sessionId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + other.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400));
    }

    @Test
    void passwordResetShouldResetPasswordAndRevokeSessions() throws Exception {
        String username = "reset-" + UUID.randomUUID();
        String email = username.toLowerCase() + "@example.com";
        AuthSession session = register(username, "browser", "dev-a", email);

        mockMvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isOk());

        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendResetToken(eq(email), token.capture(), any(LocalDateTime.class));

        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "token", token.getValue(),
                                "newPassword", "new-password"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username, "password", "test-password"))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username, "password", "new-password"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", session.refreshToken()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void passwordResetShouldNotLeakUnknownAccount() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "ghost@example.com"))))
                .andExpect(status().isOk());

        verify(mailService, never()).sendResetToken(any(), any(), any());
    }

    @Test
    void passwordResetConfirmShouldRejectInvalidToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "token", "bogus",
                                "newPassword", "new-password"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void disabledUserShouldBeRejectedAtLogin() throws Exception {
        String username = "disabled-" + UUID.randomUUID();
        register(username, "browser", "dev-a", null);
        User user = users.findByUsername(username).orElseThrow();
        user.setEnabled(false);
        users.save(user);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username, "password", "test-password"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }

    private AuthSession register(String username, String deviceName, String deviceId, String email)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(jsonCredentials(username, deviceName, deviceId, email))))
                .andExpect(status().isCreated())
                .andReturn();
        return readSession(result);
    }

    private AuthSession login(String username, String deviceId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username, "password", "test-password",
                                "deviceName", "browser", "deviceId", deviceId))))
                .andExpect(status().isOk())
                .andReturn();
        return readSession(result);
    }

    private AuthSession readSession(MvcResult result) throws Exception {
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        return new AuthSession(data.path("token").asText(), data.path("refreshToken").asText(),
                data.path("sessionId").asLong(), data.path("username").asText());
    }

    private Map<String, Object> jsonCredentials(String username, String deviceName,
                                                String deviceId, String email) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("username", username);
        body.put("password", "test-password");
        body.put("deviceName", deviceName);
        body.put("deviceId", deviceId);
        if (email != null) {
            body.put("email", email);
        }
        return body;
    }

    private record AuthSession(String token, String refreshToken, long sessionId, String username) {
    }
}
