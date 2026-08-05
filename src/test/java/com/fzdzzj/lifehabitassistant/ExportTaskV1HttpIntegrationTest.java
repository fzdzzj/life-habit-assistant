package com.fzdzzj.lifehabitassistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fzdzzj.lifehabitassistant.pojo.ExportFormat;
import com.fzdzzj.lifehabitassistant.pojo.ExportReportType;
import com.fzdzzj.lifehabitassistant.pojo.ExportTask;
import com.fzdzzj.lifehabitassistant.pojo.ExportTaskStatus;
import com.fzdzzj.lifehabitassistant.pojo.User;
import com.fzdzzj.lifehabitassistant.server.dao.ExportTaskRepository;
import com.fzdzzj.lifehabitassistant.server.dao.UserRepository;
import com.fzdzzj.lifehabitassistant.server.service.ExportTaskService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.DayOfWeek;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExportTaskV1HttpIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ExportTaskRepository tasks;
    @Autowired
    private UserRepository users;
    @Autowired
    private ExportTaskService exports;
    @Autowired
    private EntityManager em;
    @Autowired
    private PlatformTransactionManager txManager;

    @Test
    void v1ExportTasksShouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/export-tasks"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    void listShouldBeScopedToCurrentUser() throws Exception {
        AuthSession firstSession = register("v1a-" + UUID.randomUUID());
        AuthSession secondSession = register("v1b-" + UUID.randomUUID());
        String firstToken = firstSession.token();
        String secondToken = secondSession.token();
        saveHabit(firstToken, LocalDate.now());
        createTask(firstToken);
        createTask(secondToken);

        MvcResult first = mockMvc.perform(get("/api/v1/export-tasks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + firstToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").isNumber())
                .andExpect(jsonPath("$.data.content[0].status").isString())
                .andReturn();
        MvcResult second = mockMvc.perform(get("/api/v1/export-tasks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + secondToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode secondData = objectMapper.readTree(second.getResponse().getContentAsString()).path("data");
        assertEquals(1, secondData.path("totalElements").asLong());
    }

    @Test
    void listShouldFilterByStatus() throws Exception {
        AuthSession session = register("v1filter-" + UUID.randomUUID());
        User user = users.findByUsername(session.username()).orElseThrow();
        tasks.saveAndFlush(failedTask(user));
        tasks.saveAndFlush(succeededTask(user));
        tasks.saveAndFlush(cancelledTask(user));

        mockMvc.perform(get("/api/v1/export-tasks")
                        .param("status", "FAILED")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].status").value("FAILED"));

        mockMvc.perform(get("/api/v1/export-tasks")
                        .param("status", "CANCELLED")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].status").value("CANCELLED"));

        mockMvc.perform(get("/api/v1/export-tasks")
                        .param("status", "BOGUS")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void cancelShouldBlockDownloadAndRejectFinishedTasks() throws Exception {
        AuthSession session = register("v1cancel-" + UUID.randomUUID());
        User user = users.findByUsername(session.username()).orElseThrow();
        ExportTask pending = tasks.saveAndFlush(new ExportTask(user, ExportReportType.WEEKLY,
                ExportFormat.XLSX, LocalDate.now().minusDays(6), LocalDate.now()));
        ExportTask succeeded = tasks.saveAndFlush(succeededTask(user));

        mockMvc.perform(post("/api/v1/export-tasks/{id}/cancel", pending.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        mockMvc.perform(get("/api/export-tasks/{id}/download", pending.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.token()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40900));

        mockMvc.perform(post("/api/v1/export-tasks/{id}/cancel", pending.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.token()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40900));

        mockMvc.perform(post("/api/v1/export-tasks/{id}/cancel", succeeded.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.token()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40900));
    }

    @Test
    void cancelShouldReturnNotFoundForOtherUsersTask() throws Exception {
        AuthSession ownerSession = register("v1owner-" + UUID.randomUUID());
        AuthSession otherSession = register("v1other-" + UUID.randomUUID());
        User owner = users.findByUsername(ownerSession.username()).orElseThrow();
        ExportTask pending = tasks.saveAndFlush(new ExportTask(owner, ExportReportType.WEEKLY,
                ExportFormat.XLSX, LocalDate.now().minusDays(6), LocalDate.now()));

        mockMvc.perform(post("/api/v1/export-tasks/{id}/cancel", pending.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherSession.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400));
    }

    @Test
    void retryShouldReprocessFailedTaskAndAllowDownload() throws Exception {
        AuthSession session = register("v1retry-" + UUID.randomUUID());
        String token = session.token();
        saveHabit(token, LocalDate.now());
        User user = users.findByUsername(session.username()).orElseThrow();
        ExportTask failed = failedTask(user);
        tasks.saveAndFlush(failed);

        mockMvc.perform(post("/api/v1/export-tasks/{id}/retry", failed.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", anyOf(is("PENDING"), is("RUNNING"))));

        waitUntilSucceeded(token, failed.getId());

        mockMvc.perform(get("/api/export-tasks/{id}/download", failed.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void retryShouldRejectNonFailedTask() throws Exception {
        AuthSession session = register("v1retrybad-" + UUID.randomUUID());
        User user = users.findByUsername(session.username()).orElseThrow();
        ExportTask succeeded = tasks.saveAndFlush(succeededTask(user));

        mockMvc.perform(post("/api/v1/export-tasks/{id}/retry", succeeded.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.token()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40900));
    }

    @Test
    void listShouldRejectTooDeepPage() throws Exception {
        AuthSession session = register("v1deep-" + UUID.randomUUID());

        mockMvc.perform(get("/api/v1/export-tasks")
                        .param("page", "1000")
                        .param("size", "20")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void cleanupShouldDeleteOnlyExpiredSucceededTasks() throws Exception {
        AuthSession session = register("v1clean-" + UUID.randomUUID());
        User user = users.findByUsername(session.username()).orElseThrow();
        ExportTask oldSucceeded = tasks.saveAndFlush(succeededTask(user));
        ExportTask freshSucceeded = tasks.saveAndFlush(succeededTask(user));
        ExportTask oldFailed = tasks.saveAndFlush(failedTask(user));

        emBackdate(oldSucceeded.getId(), LocalDateTime.now().minusDays(30));
        emBackdate(oldFailed.getId(), LocalDateTime.now().minusDays(30));

        exports.cleanupExpired();

        assertTrue(tasks.findById(oldSucceeded.getId()).isEmpty());
        assertTrue(tasks.findById(freshSucceeded.getId()).isPresent());
        assertTrue(tasks.findById(oldFailed.getId()).isPresent());
    }

    private void emBackdate(Long id, LocalDateTime createdAt) {
        new TransactionTemplate(txManager).executeWithoutResult(status ->
                em.createNativeQuery("UPDATE export_tasks SET created_at = :createdAt WHERE id = :id")
                        .setParameter("createdAt", createdAt)
                        .setParameter("id", id)
                        .executeUpdate());
        em.clear();
    }

    private ExportTask failedTask(User user) {
        LocalDate weekStart = LocalDate.now().with(DayOfWeek.MONDAY);
        ExportTask task = new ExportTask(user, ExportReportType.WEEKLY, ExportFormat.XLSX,
                weekStart, weekStart.plusDays(6));
        task.fail("boom");
        return task;
    }

    private ExportTask succeededTask(User user) {
        ExportTask task = new ExportTask(user, ExportReportType.CUSTOM, ExportFormat.XLSX,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30));
        task.succeed("export/succeeded.xlsx", "life-habit-custom-2026-01-01_2026-06-30.xlsx");
        return task;
    }

    private ExportTask cancelledTask(User user) {
        ExportTask task = new ExportTask(user, ExportReportType.CUSTOM, ExportFormat.XLSX,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30));
        task.cancel();
        return task;
    }

    private long createTask(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/export-tasks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("type", "weekly")
                        .param("format", "xlsx"))
                .andExpect(status().isAccepted())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    private void waitUntilSucceeded(String token, long id) throws Exception {
        long deadline = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < deadline) {
            MvcResult result = mockMvc.perform(get("/api/export-tasks/{id}", id)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
            String status = data.path("status").asText();
            if ("SUCCEEDED".equals(status)) {
                return;
            }
            if ("FAILED".equals(status)) {
                fail("导出任务失败：" + data.path("errorMessage").asText());
            }
            Thread.sleep(50);
        }
        fail("导出任务未在 10 秒内完成");
    }

    private AuthSession register(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", "test-password"))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return new AuthSession(response.path("data").path("token").asText(), username);
    }

    private void saveHabit(String token, LocalDate date) throws Exception {
        mockMvc.perform(post("/api/habits")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "recordDate", date.toString(),
                                "dietScore", 4,
                                "note", "v1 export test"))))
                .andExpect(status().isOk());
    }

    private record AuthSession(String token, String username) {
    }
}
