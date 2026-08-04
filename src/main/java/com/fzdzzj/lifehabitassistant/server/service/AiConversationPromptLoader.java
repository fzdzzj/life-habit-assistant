package com.fzdzzj.lifehabitassistant.server.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Loads the conversation system prompt from
 * {@code prompts/ai-conversation-system-<version>.txt} once at startup so a
 * missing file or illegal version fails fast instead of being mistaken for a
 * provider failure per request.
 */
@Component
public class AiConversationPromptLoader {
    private static final String PROMPT_PATH_PREFIX = "prompts/ai-conversation-system-";
    private static final String PROMPT_PATH_SUFFIX = ".txt";

    private final String prompt;

    public AiConversationPromptLoader(AiConversationProperties properties) {
        this.prompt = load(properties.promptVersion());
    }

    public String load() {
        return prompt;
    }

    static String load(String version) {
        String safeVersion = sanitize(version);
        String path = PROMPT_PATH_PREFIX + safeVersion + PROMPT_PATH_SUFFIX;
        Resource resource = new ClassPathResource(path);
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("无法加载对话系统提示词: " + path, e);
        }
    }

    private static String sanitize(String version) {
        if (version == null || !version.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException("非法的提示词版本: " + version);
        }
        return version;
    }
}
