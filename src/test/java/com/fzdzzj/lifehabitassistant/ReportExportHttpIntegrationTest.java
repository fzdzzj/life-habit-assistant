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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportExportHttpIntegrationTest {
    private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void weeklyExportShouldReturnXlsxAttachment() throws Exception {
        LocalDate date = LocalDate.now();
        String token = register("weekly-" + UUID.randomUUID());
        saveHabit(token, date);

        MvcResult result = mockMvc.perform(get("/api/reports/weekly/export")
                        .param("week", date.toString())
                        .param("format", "xlsx")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(XLSX))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("life-habit-weekly-report.xlsx")))
                .andReturn();

        byte[] body = result.getResponse().getContentAsByteArray();
        assertEquals('P', body[0]);
        assertEquals('K', body[1]);
    }

    @Test
    void monthlyExportShouldReturnPdfAttachment() throws Exception {
        LocalDate date = LocalDate.now();
        String token = register("monthly-" + UUID.randomUUID());
        saveHabit(token, date);

        MvcResult result = mockMvc.perform(get("/api/reports/monthly/export")
                        .param("month", YearMonth.from(date).toString())
                        .param("format", "pdf")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("life-habit-monthly-report.pdf")))
                .andReturn();

        byte[] body = result.getResponse().getContentAsByteArray();
        assertEquals('%', body[0]);
        assertEquals('P', body[1]);
        assertEquals('D', body[2]);
        assertEquals('F', body[3]);
    }

    private String register(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", "test-password"))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.path("data").path("token").asText();
    }

    private void saveHabit(String token, LocalDate date) throws Exception {
        mockMvc.perform(post("/api/habits")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "recordDate", date.toString(),
                                "bedtime", "23:30",
                                "wakeTime", "07:00",
                                "dietScore", 4,
                                "exerciseMinutes", 45,
                                "waterMl", 1800,
                                "note", "report export test"))))
                .andExpect(status().isOk());
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
