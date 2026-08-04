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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GoalHttpIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void goalsShouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/goals"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    void defaultGoalsShouldReflectGlobalThresholds() throws Exception {
        String token = register("goals-default-" + UUID.randomUUID());

        mockMvc.perform(get("/api/goals").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.minimumSleepMinutes").value(420))
                .andExpect(jsonPath("$.data.maximumSleepMinutes").value(540))
                .andExpect(jsonPath("$.data.minimumHydrationMl").value(1500))
                .andExpect(jsonPath("$.data.minimumExerciseMinutes").value(30))
                .andExpect(jsonPath("$.data.minimumDietScore").value(3));
    }

    @Test
    void saveUpdateResetFlowShouldPersistCustomGoals() throws Exception {
        String token = register("goals-flow-" + UUID.randomUUID());

        putGoals(token, Map.of(
                "minimumSleepMinutes", 480, "maximumSleepMinutes", 600,
                "minimumHydrationMl", 2000, "minimumExerciseMinutes", 45, "minimumDietScore", 4))
                .andExpect(jsonPath("$.data.minimumHydrationMl").value(2000))
                .andExpect(jsonPath("$.data.minimumExerciseMinutes").value(45));
        getGoals(token)
                .andExpect(jsonPath("$.data.minimumSleepMinutes").value(480))
                .andExpect(jsonPath("$.data.minimumDietScore").value(4));

        putGoals(token, Map.of(
                "minimumSleepMinutes", 360, "maximumSleepMinutes", 480,
                "minimumHydrationMl", 1200, "minimumExerciseMinutes", 20, "minimumDietScore", 2))
                .andExpect(jsonPath("$.data.minimumSleepMinutes").value(360));
        getGoals(token)
                .andExpect(jsonPath("$.data.minimumHydrationMl").value(1200))
                .andExpect(jsonPath("$.data.minimumDietScore").value(2));

        mockMvc.perform(delete("/api/goals").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.minimumSleepMinutes").value(420))
                .andExpect(jsonPath("$.data.minimumHydrationMl").value(1500));
        getGoals(token)
                .andExpect(jsonPath("$.data.minimumSleepMinutes").value(420))
                .andExpect(jsonPath("$.data.minimumExerciseMinutes").value(30));
    }

    @Test
    void invalidGoalsShouldReturnUnifiedValidationError() throws Exception {
        String token = register("goals-invalid-" + UUID.randomUUID());

        putGoals(token, Map.of(
                "minimumSleepMinutes", 600, "maximumSleepMinutes", 480,
                "minimumHydrationMl", 1500, "minimumExerciseMinutes", 30, "minimumDietScore", 3))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
        putGoals(token, Map.of(
                "minimumSleepMinutes", 420, "maximumSleepMinutes", 540,
                "minimumHydrationMl", 100, "minimumExerciseMinutes", 30, "minimumDietScore", 3))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void goalsShouldBeIsolatedPerUser() throws Exception {
        String first = register("goals-iso-a-" + UUID.randomUUID());
        String second = register("goals-iso-b-" + UUID.randomUUID());

        putGoals(first, Map.of(
                "minimumSleepMinutes", 480, "maximumSleepMinutes", 600,
                "minimumHydrationMl", 2000, "minimumExerciseMinutes", 45, "minimumDietScore", 4));

        getGoals(first).andExpect(jsonPath("$.data.minimumHydrationMl").value(2000));
        getGoals(second).andExpect(jsonPath("$.data.minimumHydrationMl").value(1500));
    }

    @Test
    void customGoalsShouldChangeTrendAchievement() throws Exception {
        String token = register("goals-trend-" + UUID.randomUUID());
        LocalDate date = LocalDate.now().minusDays(1);
        saveHabit(token, date, 2);
        mockMvc.perform(post("/api/habits/{date}/sleep-sessions", date)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("sleepType", "NIGHT",
                                "sleepStartAt", date.minusDays(1).atTime(23, 0).toString(),
                                "wakeAt", date.atTime(7, 0).toString()))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/habits/{date}/drink-records", date)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("drinkType", "WATER", "volumeMl", 500,
                                "recordedAt", date.atTime(10, 0).toString()))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/trends").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].achieved").value(false));

        putGoals(token, Map.of(
                "minimumSleepMinutes", 180, "maximumSleepMinutes", 960,
                "minimumHydrationMl", 500, "minimumExerciseMinutes", 0, "minimumDietScore", 2));

        mockMvc.perform(get("/api/trends").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].achieved").value(true));
    }

    private org.springframework.test.web.servlet.ResultActions getGoals(String token) throws Exception {
        return mockMvc.perform(get("/api/goals").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions putGoals(String token, Map<String, Object> request) throws Exception {
        return mockMvc.perform(put("/api/goals")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request)));
    }

    private void saveHabit(String token, LocalDate date, int dietScore) throws Exception {
        mockMvc.perform(post("/api/habits")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("recordDate", date.toString(), "dietScore", dietScore))))
                .andExpect(status().isOk());
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

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
