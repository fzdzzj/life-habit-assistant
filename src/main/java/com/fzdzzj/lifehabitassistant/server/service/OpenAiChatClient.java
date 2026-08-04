package com.fzdzzj.lifehabitassistant.server.service;

import java.util.List;

/**
 * Minimal OpenAI Chat Completions adapter.
 * Implementations must not log the API key or request payload containing personal data.
 */
public interface OpenAiChatClient {
    /** @return the model's message content; throws RuntimeException on provider or parse failure. */
    String chat(String systemPrompt, String userPrompt);

    /**
     * Multi-turn chat: the sanitized history is sent as real chat messages
     * while the user prompt carries the latest message plus sanitized context.
     */
    String chat(String systemPrompt, List<ChatTurn> history, String userPrompt);

    record ChatTurn(String role, String content) {
    }
}
