package com.fzdzzj.lifehabitassistant.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class RestClientOpenAiChatClient implements OpenAiChatClient {
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final AiAdviceProperties properties;

    public RestClientOpenAiChatClient(RestClient.Builder builder, AiAdviceProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(properties.timeoutSeconds()));
        requestFactory.setReadTimeout(Duration.ofSeconds(properties.timeoutSeconds()));
        this.restClient = builder.baseUrl(properties.baseUrl()).requestFactory(requestFactory).build();
    }

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        try {
            String body = restClient.post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "model", properties.model(),
                            "temperature", 0.4,
                            "messages", List.of(
                                    Map.of("role", "system", "content", systemPrompt),
                                    Map.of("role", "user", "content", userPrompt))))
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(body);
            String content = root.path("choices").path(0).path("message").path("content").asText();
            if (content == null || content.isBlank()) {
                throw new IllegalStateException("OpenAI response contains no message content");
            }
            return content;
        } catch (IOException | RuntimeException e) {
            if (e instanceof IllegalStateException) {
                throw (IllegalStateException) e;
            }
            throw new IllegalStateException("OpenAI request failed", e);
        }
    }
}
