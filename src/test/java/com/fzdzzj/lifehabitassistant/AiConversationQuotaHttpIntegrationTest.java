package com.fzdzzj.lifehabitassistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fzdzzj.lifehabitassistant.pojo.AiQuotaPeriod;
import com.fzdzzj.lifehabitassistant.pojo.User;
import com.fzdzzj.lifehabitassistant.server.dao.AiQuotaUsageRepository;
import com.fzdzzj.lifehabitassistant.server.dao.UserRepository;
import com.fzdzzj.lifehabitassistant.server.service.OpenAiChatClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the quota-exhausted conversation path against the real
 * AiQuotaService and quota table while the provider client is mocked.
 */
@SpringBootTest(properties = {
        "app.ai.conversation.enabled=true",
        "app.ai.advice.api-key=sk-test",
        "app.ai.advice.model=gpt-test"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiConversationQuotaHttpIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository users;
    @Autowired
    private AiQuotaUsageRepository quota;

    @MockitoBean
    private OpenAiChatClient chatClient;

    @Test
    void exhaustedDailyQuotaShouldFallbackWithoutCountingOrCallingProvider() throws Exception {
        String username = "quota-" + UUID.randomUUID();
        String token = register(username);
        Long conversationId = createConversation(token);

        User user = users.findByUsername(username).orElseThrow();
        user.setAiDailyLimit(0);
        user.setAiMonthlyLimit(0);
        users.save(user);

        MvcResult result = mockMvc.perform(post("/api/v1/ai/conversations/{id}/messages", conversationId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", "帮我看看"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message.source").value("RULE_FALLBACK"))
                .andExpect(jsonPath("$.data.message.callCounted").value(false))
                .andReturn();

        assertTrue(objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("message").path("content").asText().contains("本地规则"));
        verify(chatClient, never()).chat(any(), any(), any());

        int used = quota.findUsedCount(user.getId(), AiQuotaPeriod.DAY.name(),
                LocalDate.now().toString()).orElse(-1);
        assertEquals(0, used);

        mockMvc.perform(get("/api/v1/ai/conversations/{id}/messages", conversationId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    private String register(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", "test-password",
                                "deviceName", "browser",
                                "deviceId", "dev-a"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("token").asText();
    }

    private Long createConversation(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/ai/conversations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }
}
