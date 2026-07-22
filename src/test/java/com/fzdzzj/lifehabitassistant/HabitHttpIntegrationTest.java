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

        saveHabit(firstToken, date);

        mockMvc.perform(get("/api/habits").header("Authorization", "Bearer " + firstToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1))
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

        saveHabit(token, date);
        saveHabit(token, date);

        mockMvc.perform(get("/api/habits").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].dietScore").value(4));
    }

    @Test
    void invalidBoundariesShouldReturnUnifiedValidationError() throws Exception {
        String token = register("boundary-" + UUID.randomUUID());

        mockMvc.perform(post("/api/habits")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(habitRequest(LocalDate.now().plusDays(1)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
        mockMvc.perform(post("/api/habits")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("recordDate", LocalDate.now().toString(), "dietScore", 6))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
        mockMvc.perform(get("/api/habits")
                        .header("Authorization", "Bearer " + token)
                        .param("start", LocalDate.now().minusDays(366).toString())
                        .param("end", LocalDate.now().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
        mockMvc.perform(get("/api/habits")
                        .header("Authorization", "Bearer " + token)
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void sleepSessionsShouldSeparateNightSleepAndNap() throws Exception {
        String token = register("sleep-" + UUID.randomUUID());
        LocalDate date = LocalDate.now().minusDays(1);
        saveHabit(token, date);

        mockMvc.perform(post("/api/habits/{date}/sleep-sessions", date)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("sleepType", "NIGHT", "sleepStartAt", date.minusDays(1).atTime(23, 0).toString(), "wakeAt", date.atTime(7, 0).toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sleepType").value("NIGHT"));
        mockMvc.perform(post("/api/habits/{date}/sleep-sessions", date)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("sleepType", "NAP", "sleepStartAt", date.atTime(13, 0).toString(), "wakeAt", date.atTime(13, 30).toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sleepType").value("NAP"));
        mockMvc.perform(get("/api/habits/{date}/sleep-sessions", date).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
        mockMvc.perform(get("/api/habits/{date}", date).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sleepMinutes").value(510));
    }

    @Test
    void exerciseSessionsShouldAggregateDetailsAndValidateOtherType() throws Exception {
        String token = register("exercise-" + UUID.randomUUID());
        LocalDate date = LocalDate.now().minusDays(1);
        saveHabit(token, date);

        mockMvc.perform(post("/api/habits/{date}/exercise-sessions", date)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("exerciseType", "RUN", "intensity", "HIGH", "durationMinutes", 30, "startedAt", date.atTime(18, 0).toString(), "distanceKm", 5.0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.moderateEquivalentMinutes").value(60));
        mockMvc.perform(post("/api/habits/{date}/exercise-sessions", date)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("exerciseType", "OTHER", "intensity", "LOW", "durationMinutes", 20, "startedAt", date.atTime(20, 0).toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
        mockMvc.perform(get("/api/habits/{date}", date).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.exerciseMinutes").value(30))
                .andExpect(jsonPath("$.data.moderateEquivalentExerciseMinutes").value(60));
    }

    @Test
    void drinkRecordsShouldCalculateHydrationAndFlagRiskDrinks() throws Exception {
        String token = register("drink-" + UUID.randomUUID());
        LocalDate date = LocalDate.now().minusDays(1);
        saveHabit(token, date);

        createDrink(token, date, Map.of("drinkType", "WATER", "volumeMl", 500, "recordedAt", date.atTime(9, 0).toString()))
                .andExpect(jsonPath("$.data.hydrationMl").value(500))
                .andExpect(jsonPath("$.data.riskDrink").value(false));
        createDrink(token, date, Map.of("drinkType", "COFFEE", "volumeMl", 200, "recordedAt", date.atTime(10, 0).toString()))
                .andExpect(jsonPath("$.data.hydrationMl").value(160));
        createDrink(token, date, Map.of("drinkType", "CARBONATED_SWEET_DRINK", "volumeMl", 330, "recordedAt", date.atTime(12, 0).toString()))
                .andExpect(jsonPath("$.data.hydrationMl").value(0))
                .andExpect(jsonPath("$.data.riskDrink").value(true));
        createDrink(token, date, Map.of("drinkType", "OTHER", "volumeMl", 100, "recordedAt", date.atTime(13, 0).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));

        mockMvc.perform(get("/api/habits/{date}", date).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hydrationMl").value(660))
                .andExpect(jsonPath("$.data.riskDrinkVolumeMl").value(330));
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

    private org.springframework.test.web.servlet.ResultActions createDrink(String token, LocalDate date, Map<String, Object> request) throws Exception {
        return mockMvc.perform(post("/api/habits/{date}/drink-records", date)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request)));
    }

    private void saveHabit(String token, LocalDate date) throws Exception {
        mockMvc.perform(post("/api/habits")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(habitRequest(date))))
                .andExpect(status().isOk());
    }

    private Map<String, Object> habitRequest(LocalDate date) {
        return Map.of(
                "recordDate", date.toString(),
                "dietScore", 4,
                "note", "integration test");
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
