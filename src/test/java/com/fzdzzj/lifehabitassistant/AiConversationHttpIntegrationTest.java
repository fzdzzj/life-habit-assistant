package com.fzdzzj.lifehabitassistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fzdzzj.lifehabitassistant.server.dao.AiConversationMessageRepository;
import com.fzdzzj.lifehabitassistant.server.dao.AiConversationRepository;
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

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiConversationHttpIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository users;
    @Autowired
    private AiConversationRepository conversations;
    @Autowired
    private AiConversationMessageRepository messages;

    @Test
    void endpointsShouldRequireAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/ai/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
        mockMvc.perform(get("/api/v1/ai/conversations"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/ai/conversations/1/messages"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/ai/conversations/1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", "你好"))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/ai/conversations/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createListAndDeleteConversationFlow() throws Exception {
        String token = register("conv-flow-" + UUID.randomUUID());

        Long id = createConversation(token, "我的对话");

        mockMvc.perform(get("/api/v1/ai/conversations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(id))
                .andExpect(jsonPath("$.data.content[0].title").value("我的对话"));

        mockMvc.perform(delete("/api/v1/ai/conversations/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/ai/conversations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void sendShouldPersistUserAndFallbackMessages() throws Exception {
        String token = register("conv-send-" + UUID.randomUUID());
        Long id = createConversation(token, null);

        MvcResult result = mockMvc.perform(post("/api/v1/ai/conversations/{id}/messages", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", "我今天状态如何？"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message.role").value("ASSISTANT"))
                .andExpect(jsonPath("$.data.message.source").value("RULE_FALLBACK"))
                .andExpect(jsonPath("$.data.message.callCounted").value(false))
                .andReturn();

        assertTrue(objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("message").path("content").asText().contains("本地规则"));

        mockMvc.perform(get("/api/v1/ai/conversations/{id}/messages", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].role").value("USER"))
                .andExpect(jsonPath("$.data[0].content").value("我今天状态如何？"))
                .andExpect(jsonPath("$.data[1].role").value("ASSISTANT"));
    }

    @Test
    void conversationsAndMessagesShouldBeIsolatedBetweenUsers() throws Exception {
        String owner = register("conv-owner-" + UUID.randomUUID());
        String other = register("conv-other-" + UUID.randomUUID());
        Long id = createConversation(owner, "私密对话");

        mockMvc.perform(get("/api/v1/ai/conversations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        mockMvc.perform(get("/api/v1/ai/conversations/{id}/messages", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + other))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400));

        mockMvc.perform(post("/api/v1/ai/conversations/{id}/messages", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", "偷看"))))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/ai/conversations/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + other))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteShouldCascadeMessages() throws Exception {
        String username = "conv-cascade-" + UUID.randomUUID();
        String token = register(username);
        Long id = createConversation(token, "待删除");
        send(token, id, "第一条");
        send(token, id, "第二条");
        assertEquals(4, messages.findByConversationIdOrderByIdAsc(id).size());

        mockMvc.perform(delete("/api/v1/ai/conversations/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        assertTrue(conversations.findByIdAndUserId(id,
                users.findByUsername(username).orElseThrow().getId()).isEmpty());
    }

    @Test
    void messagesShouldBeReturnedInAscendingOrder() throws Exception {
        String token = register("conv-order-" + UUID.randomUUID());
        Long id = createConversation(token, null);
        send(token, id, "第一问");
        send(token, id, "第二问");

        mockMvc.perform(get("/api/v1/ai/conversations/{id}/messages", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data[0].role").value("USER"))
                .andExpect(jsonPath("$.data[1].role").value("ASSISTANT"))
                .andExpect(jsonPath("$.data[2].role").value("USER"))
                .andExpect(jsonPath("$.data[3].role").value("ASSISTANT"));
    }

    @Test
    void oversizedMessageShouldBeRejected() throws Exception {
        String token = register("conv-size-" + UUID.randomUUID());
        Long id = createConversation(token, null);
        String longContent = "长".repeat(2001);

        mockMvc.perform(post("/api/v1/ai/conversations/{id}/messages", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", longContent))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void conversationListShouldPaginate() throws Exception {
        String token = register("conv-page-" + UUID.randomUUID());
        createConversation(token, "一");
        createConversation(token, "二");
        createConversation(token, "三");

        mockMvc.perform(get("/api/v1/ai/conversations?page=0&size=2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(3));

        mockMvc.perform(get("/api/v1/ai/conversations?page=1&size=2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1));
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

    private Long createConversation(String token, String title) throws Exception {
        Map<String, Object> body = title == null ? Map.of() : Map.of("title", title);
        MvcResult result = mockMvc.perform(post("/api/v1/ai/conversations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    private void send(String token, Long id, String content) throws Exception {
        mockMvc.perform(post("/api/v1/ai/conversations/{id}/messages", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", content))))
                .andExpect(status().isOk());
    }
}
