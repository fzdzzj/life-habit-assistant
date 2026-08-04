package com.fzdzzj.lifehabitassistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExportTaskHttpIntegrationTest {
    private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void exportTasksShouldRequireAuthentication() throws Exception {
        mockMvc.perform(post("/api/export-tasks")
                        .param("type", "weekly")
                        .param("format", "xlsx"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    void customExportTaskShouldCompleteAndDownloadXlsx() throws Exception {
        String token = register("expc-" + UUID.randomUUID());
        saveHabit(token, LocalDate.now());
        LocalDate start = LocalDate.now().minusDays(29);
        LocalDate end = LocalDate.now();

        long id = createTask(token, Map.of(
                "type", "custom",
                "format", "xlsx",
                "start", start.toString(),
                "end", end.toString()));

        waitUntilSucceeded(token, id);

        MvcResult result = mockMvc.perform(get("/api/export-tasks/{id}/download", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(XLSX))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("life-habit-custom-")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString(".xlsx")))
                .andReturn();

        byte[] body = result.getResponse().getContentAsByteArray();
        assertEquals('P', body[0]);
        assertEquals('K', body[1]);
    }

    @Test
    void monthlyExportTaskShouldDownloadPdf() throws Exception {
        String token = register("expm-" + UUID.randomUUID());
        saveHabit(token, LocalDate.now());

        long id = createTask(token, Map.of(
                "type", "monthly",
                "format", "pdf",
                "month", YearMonth.now().toString()));

        waitUntilSucceeded(token, id);

        MvcResult result = mockMvc.perform(get("/api/export-tasks/{id}/download", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("life-habit-monthly-")))
                .andReturn();

        byte[] body = result.getResponse().getContentAsByteArray();
        assertEquals('%', body[0]);
        assertEquals('P', body[1]);
        assertEquals('D', body[2]);
        assertEquals('F', body[3]);
    }

    @Test
    void usersShouldNotSeeOthersExportTasks() throws Exception {
        String firstToken = register("expf-" + UUID.randomUUID());
        String secondToken = register("exps-" + UUID.randomUUID());
        long id = createTask(firstToken, Map.of("type", "weekly", "format", "xlsx"));

        mockMvc.perform(get("/api/export-tasks/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + secondToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400));
        mockMvc.perform(get("/api/export-tasks/{id}/download", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + secondToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400));
    }

    @Test
    void customExportRejectsInvalidRanges() throws Exception {
        String token = register("expi-" + UUID.randomUUID());

        mockMvc.perform(post("/api/export-tasks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("type", "custom")
                        .param("format", "xlsx")
                        .param("start", LocalDate.now().toString())
                        .param("end", LocalDate.now().minusDays(1).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
        mockMvc.perform(post("/api/export-tasks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("type", "custom")
                        .param("format", "xlsx")
                        .param("start", LocalDate.now().toString())
                        .param("end", LocalDate.now().plusDays(1).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
        mockMvc.perform(post("/api/export-tasks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("type", "custom")
                        .param("format", "xlsx")
                        .param("start", "2000-01-01")
                        .param("end", LocalDate.now().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    private long createTask(String token, Map<String, String> params) throws Exception {
        var request = post("/api/export-tasks")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        for (Map.Entry<String, String> entry : params.entrySet()) {
            request = request.param(entry.getKey(), entry.getValue());
        }
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.path("data").path("id").asLong();
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

    private String register(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", "test-password"))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.path("data").path("token").asText();
    }

    private void saveHabit(String token, LocalDate date) throws Exception {
        mockMvc.perform(post("/api/habits")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "recordDate", date.toString(),
                                "dietScore", 4,
                                "note", "export task test"))))
                .andExpect(status().isOk());
    }
}
