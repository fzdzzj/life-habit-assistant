package com.fzdzzj.lifehabitassistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HabitHttpIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void habitsShouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/habits"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    void habitsShouldRejectInvalidJwt() throws Exception {
        mockMvc.perform(get("/api/habits").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    void usersShouldOnlySeeTheirOwnHabitRecords() throws Exception {
        String firstToken = register("first-" + UUID.randomUUID());
        String secondToken = register("second-" + UUID.randomUUID());
        LocalDate date = LocalDate.now().minusDays(1);

        saveHabit(firstToken, date, 30, 1800);

        mockMvc.perform(get("/api/habits").header("Authorization", "Bearer " + firstToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].recordDate").value(date.toString()));
        mockMvc.perform(get("/api/habits").header("Authorization", "Bearer " + secondToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0));
    }

    @Test
    void repeatedDateShouldUpdateRatherThanDuplicateRecord() throws Exception {
        String token = register("update-" + UUID.randomUUID());
        LocalDate date = LocalDate.now().minusDays(1);

        saveHabit(token, date, 20, 1000);
        saveHabit(token, date, 60, 2200);

        mockMvc.perform(get("/api/habits").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].exerciseMinutes").value(60))
                .andExpect(jsonPath("$.data.content[0].waterMl").value(2200));
    }

    private String register(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", "test-password"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.path("data").path("token").asText();
    }

    private void saveHabit(String token, LocalDate date, int exerciseMinutes, int waterMl) throws Exception {
        mockMvc.perform(post("/api/habits")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "recordDate", date.toString(),
                                "bedtime", "23:30",
                                "wakeTime", "07:00",
                                "dietScore", 4,
                                "exerciseMinutes", exerciseMinutes,
                                "waterMl", waterMl,
                                "note", "integration test"))))
                .andExpect(status().isOk());
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
