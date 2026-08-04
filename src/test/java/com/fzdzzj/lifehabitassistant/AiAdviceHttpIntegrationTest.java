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

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiAdviceHttpIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void aiEndpointsShouldRequireAuthentication() throws Exception {
        mockMvc.perform(post("/api/ai/analyses"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    void disabledConfigShouldFallBackToRuleAdviceAndPersistHistory() throws Exception {
        String token = register("ai-" + UUID.randomUUID());

        mockMvc.perform(post("/api/ai/analyses?days=7").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("RULE_FALLBACK"))
                .andExpect(jsonPath("$.data.content.periodSummary").isNotEmpty())
                .andExpect(jsonPath("$.data.dailyUsed").value(0))
                .andExpect(jsonPath("$.data.dailyLimit").value(3))
                .andExpect(jsonPath("$.data.monthlyLimit").value(30))
                .andExpect(jsonPath("$.data.cached").value(false));
    }

    @Test
    void refreshParameterShouldBeAcceptedAndFallbackStillNotCached() throws Exception {
        String token = register("refresh-" + UUID.randomUUID());

        mockMvc.perform(post("/api/ai/analyses?days=7&refresh=true")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("RULE_FALLBACK"))
                .andExpect(jsonPath("$.data.cached").value(false));
    }

    @Test
    void weeklyReportShouldAttachLatestSavedAdviceWithoutCallingAi() throws Exception {
        String token = register("report-ai-" + UUID.randomUUID());

        mockMvc.perform(post("/api/ai/reports/weekly").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("RULE_FALLBACK"));

        mockMvc.perform(get("/api/reports/weekly").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiAdvice").exists())
                .andExpect(jsonPath("$.data.aiAdvice.source").value("RULE_FALLBACK"));
    }

    @Test
    void usersShouldOnlySeeTheirOwnSavedAdvice() throws Exception {
        String firstToken = register("isolation-" + UUID.randomUUID());
        String secondToken = register("isolation-" + UUID.randomUUID());

        mockMvc.perform(post("/api/ai/reports/monthly").header("Authorization", "Bearer " + firstToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/reports/monthly").header("Authorization", "Bearer " + firstToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiAdvice").exists());
        mockMvc.perform(get("/api/reports/monthly").header("Authorization", "Bearer " + secondToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiAdvice").value(org.hamcrest.Matchers.nullValue()));
    }

    private String register(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", username, "password", "test-password"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.path("data").path("token").asText();
    }
}
