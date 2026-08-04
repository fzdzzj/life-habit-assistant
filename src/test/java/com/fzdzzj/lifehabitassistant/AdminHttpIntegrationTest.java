package com.fzdzzj.lifehabitassistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fzdzzj.lifehabitassistant.pojo.ExportFormat;
import com.fzdzzj.lifehabitassistant.pojo.ExportReportType;
import com.fzdzzj.lifehabitassistant.pojo.ExportTask;
import com.fzdzzj.lifehabitassistant.pojo.Role;
import com.fzdzzj.lifehabitassistant.pojo.User;
import com.fzdzzj.lifehabitassistant.server.dao.ExportTaskRepository;
import com.fzdzzj.lifehabitassistant.server.dao.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminHttpIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository users;
    @Autowired
    private ExportTaskRepository tasks;

    @Test
    void adminEndpointsShouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    void ordinaryUserShouldGetForbidden() throws Exception {
        AuthSession user = register("plain-" + UUID.randomUUID());

        mockMvc.perform(get("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }

    @Test
    void adminShouldListUsersAndSeeOverview() throws Exception {
        AuthSession admin = adminSession("boss-" + UUID.randomUUID());
        AuthSession user = register("staff-" + UUID.randomUUID());

        mockMvc.perform(get("/api/v1/admin/users")
                        .param("search", user.username())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].username").value(user.username()))
                .andExpect(jsonPath("$.data.content[0].role").value("USER"))
                .andExpect(jsonPath("$.data.content[0].email").value(org.hamcrest.Matchers.nullValue()));

        Long id = users.findByUsername(user.username()).orElseThrow().getId();
        mockMvc.perform(get("/api/v1/admin/users/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(user.username()))
                .andExpect(jsonPath("$.data.habitRecords").isNumber())
                .andExpect(jsonPath("$.data.dailyLimit").value(3));
    }

    @Test
    void adminShouldPromoteAndDemoteWithLastAdminProtection() throws Exception {
        AuthSession admin = adminSession("boss2-" + UUID.randomUUID());
        Long adminId = users.findByUsername(admin.username()).orElseThrow().getId();
        AuthSession staff = register("staff2-" + UUID.randomUUID());
        Long staffId = users.findByUsername(staff.username()).orElseThrow().getId();

        mockMvc.perform(patch("/api/v1/admin/users/{id}", staffId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("role", "ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ADMIN"));

        // Two admins now: demoting the original is allowed.
        mockMvc.perform(patch("/api/v1/admin/users/{id}", adminId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("role", "USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("USER"));

        // Only staff is an enabled admin left: demoting them must fail.
        mockMvc.perform(patch("/api/v1/admin/users/{id}", staffId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + staff.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("role", "USER"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void adminShouldNotDisableTheLastEnabledAdmin() throws Exception {
        AuthSession admin = adminSession("solo-" + UUID.randomUUID());
        Long adminId = users.findByUsername(admin.username()).orElseThrow().getId();

        mockMvc.perform(post("/api/v1/admin/users/{id}/disable", adminId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void disableShouldRevokeSessionsAndBlockLogin() throws Exception {
        AuthSession admin = adminSession("boss3-" + UUID.randomUUID());
        AuthSession victim = register("victim-" + UUID.randomUUID());
        Long victimId = users.findByUsername(victim.username()).orElseThrow().getId();

        mockMvc.perform(post("/api/v1/admin/users/{id}/disable", victimId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", victim.username(), "password", "test-password"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", victim.refreshToken()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminShouldAdjustQuotaAndResetToGlobal() throws Exception {
        AuthSession admin = adminSession("quota-admin-" + UUID.randomUUID());
        AuthSession user = register("quota-user-" + UUID.randomUUID());
        Long userId = users.findByUsername(user.username()).orElseThrow().getId();

        mockMvc.perform(patch("/api/v1/admin/quotas/{userId}", userId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("dailyLimit", 5, "monthlyLimit", 60))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dailyLimit").value(5))
                .andExpect(jsonPath("$.data.monthlyLimit").value(60));

        mockMvc.perform(get("/api/v1/admin/quotas")
                        .param("search", user.username())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].dailyLimit").value(5));

        Map<String, Object> reset = new LinkedHashMap<>();
        reset.put("dailyLimit", null);
        reset.put("monthlyLimit", null);
        mockMvc.perform(patch("/api/v1/admin/quotas/{userId}", userId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reset)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dailyLimit").value(3))
                .andExpect(jsonPath("$.data.monthlyLimit").value(30));
    }

    @Test
    void adminShouldCancelAnyUsersExportTask() throws Exception {
        AuthSession admin = adminSession("task-admin-" + UUID.randomUUID());
        AuthSession owner = register("task-owner-" + UUID.randomUUID());
        User ownerUser = users.findByUsername(owner.username()).orElseThrow();
        ExportTask task = tasks.saveAndFlush(new ExportTask(ownerUser, ExportReportType.WEEKLY,
                ExportFormat.XLSX, LocalDate.now().minusDays(6), LocalDate.now()));

        mockMvc.perform(get("/api/v1/admin/export-tasks")
                        .param("status", "PENDING")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].username").value(owner.username()));

        mockMvc.perform(post("/api/v1/admin/export-tasks/{id}/cancel", task.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        mockMvc.perform(get("/api/v1/export-tasks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].status").value("CANCELLED"));
    }

    @Test
    void statsShouldReturnSystemOverview() throws Exception {
        AuthSession admin = adminSession("stats-admin-" + UUID.randomUUID());

        mockMvc.perform(get("/api/v1/admin/stats")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalUsers").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.adminUsers").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.exportTasksByStatus.PENDING").isNumber())
                .andExpect(jsonPath("$.data.todayAiCalls").isNumber());
    }

    private AuthSession adminSession(String username) throws Exception {
        AuthSession session = register(username);
        User user = users.findByUsername(username).orElseThrow();
        user.changeRole(Role.ADMIN);
        users.save(user);
        return session;
    }

    private AuthSession register(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", "test-password"))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        return new AuthSession(data.path("token").asText(), data.path("refreshToken").asText(),
                data.path("username").asText());
    }

    private record AuthSession(String token, String refreshToken, String username) {
    }
}
