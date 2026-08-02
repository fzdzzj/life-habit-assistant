package com.fzdzzj.lifehabitassistant.server.service;

/**
 * Minimal OpenAI Chat Completions adapter.
 * Implementations must not log the API key or request payload containing personal data.
 */
public interface OpenAiChatClient {
    /** @return the model's message content; throws RuntimeException on provider or parse failure. */
    String chat(String systemPrompt, String userPrompt);
}
