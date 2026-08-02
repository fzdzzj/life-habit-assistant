package com.fzdzzj.lifehabitassistant.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fzdzzj.lifehabitassistant.pojo.AiAdviceDtos;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class AiAdviceContentParser {
    private static final int MAX_RECOMMENDATIONS = 3;

    private final ObjectMapper objectMapper;

    public AiAdviceContentParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AiAdviceDtos.AiAdviceContent parse(String raw) {
        try {
            JsonNode node = objectMapper.readTree(stripFences(raw));
            if (!node.isObject()) {
                throw new IllegalArgumentException("AI 返回内容必须是 JSON 对象");
            }
            return new AiAdviceDtos.AiAdviceContent(
                    text(node, "periodSummary"),
                    text(node, "riskExplanation"),
                    recommendations(node),
                    text(node, "nextPeriodPlan"),
                    text(node, "encouragement"),
                    text(node, "disclaimer"));
        } catch (IOException | IllegalArgumentException e) {
            if (e instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) e;
            }
            throw new IllegalArgumentException("AI 返回内容无法解析", e);
        }
    }

    private String stripFences(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline >= 0 && lastFence > firstNewline) {
                return trimmed.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return trimmed;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText() : "";
    }

    private List<String> recommendations(JsonNode node) {
        JsonNode value = node.path("recommendations");
        if (!value.isArray()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (JsonNode item : value) {
            if (result.size() >= MAX_RECOMMENDATIONS) {
                break;
            }
            if (item.isTextual() && !item.asText().isBlank()) {
                result.add(item.asText());
            }
        }
        return List.copyOf(result);
    }
}
