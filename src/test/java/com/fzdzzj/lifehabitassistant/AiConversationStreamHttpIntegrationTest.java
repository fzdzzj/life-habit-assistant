package com.fzdzzj.lifehabitassistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real-port SSE test: MockMvc re-enters the security filter chain during async
 * dispatch and rejects the already-committed SSE response, so streaming is
 * verified against a running server instead.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AiConversationStreamHttpIntegrationTest {
    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private ObjectMapper objectMapper;
    @Value("${local.server.port}")
    private int port;

    @Test
    void streamShouldReturnFallbackSseEventWhenModelUnavailable() throws Exception {
        String token = register("stream-" + UUID.randomUUID());
        Long id = createConversation(token, null);

        String body = postStream(token, id, "今天状态如何");

        assertTrue(body.contains("event:fallback"));
        assertTrue(body.contains("本地规则"));
    }

    @Test
    void streamShouldPersistFallbackMessageAfterSseCompletes() throws Exception {
        String token = register("stream-p-" + UUID.randomUUID());
        Long id = createConversation(token, null);

        postStream(token, id, "帮我看看");

        HttpHeaders getHeaders = new HttpHeaders();
        getHeaders.setBearerAuth(token);
        ResponseEntity<String> history = rest.exchange(
                "/api/v1/ai/conversations/{id}/messages", org.springframework.http.HttpMethod.GET,
                new HttpEntity<>(getHeaders), String.class, id);
        assertTrue(history.getBody().contains("本地规则"));
    }

    private String register(String username) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(Map.of(
                "username", username,
                "password", "test-password",
                "deviceName", "browser",
                "deviceId", "dev-a")), headers);
        ResponseEntity<String> response = rest.postForEntity("/api/auth/register", entity, String.class);
        return objectMapper.readTree(response.getBody()).path("data").path("token").asText();
    }

    private Long createConversation(String token, String title) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = title == null ? Map.of() : Map.of("title", title);
        HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
        ResponseEntity<String> response = rest.postForEntity("/api/v1/ai/conversations", entity, String.class);
        return objectMapper.readTree(response.getBody()).path("data").path("id").asLong();
    }

    private String postStream(String token, Long id, String content) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) URI.create(
                "http://localhost:" + port + "/api/v1/ai/conversations/" + id + "/messages/stream")
                .toURL().openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Authorization", "Bearer " + token);
        connection.setRequestProperty("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        byte[] payload = objectMapper.writeValueAsBytes(Map.of("content", content));
        connection.setFixedLengthStreamingMode(payload.length);
        try (OutputStream out = connection.getOutputStream()) {
            out.write(payload);
        }
        int status = connection.getResponseCode();
        assertEquals(200, status);
        String body = new String(connection.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        connection.disconnect();
        return body;
    }
}
