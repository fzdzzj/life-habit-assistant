package com.fzdzzj.lifehabitassistant.server.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Loads the AI system prompt from {@code prompts/ai-advice-system-<version>.txt}
 * so prompt text is versioned and editable without touching Java code.
 *
 * <p>The prompt is read once at bean creation: a missing file or illegal
 * version fails startup instead of surfacing as a per-request error (which
 * would otherwise be mistaken for a provider failure and counted against quota).
 */
@Component
public class AiSystemPromptLoader {
    private static final String PROMPT_PATH_PREFIX = "prompts/ai-advice-system-";
    private static final String PROMPT_PATH_SUFFIX = ".txt";

    private final String prompt;

    public AiSystemPromptLoader(AiAdviceProperties properties) {
        this.prompt = load(properties.promptVersion());
    }

    /** Returns the prompt cached at startup. */
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
            throw new IllegalStateException("无法加载 AI 系统提示词: " + path, e);
        }
    }

    private static String sanitize(String version) {
        if (version == null || !version.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException("非法的提示词版本: " + version);
        }
        return version;
    }
}
