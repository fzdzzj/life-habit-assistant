package com.fzdzzj.lifehabitassistant.server.service;

import reactor.core.publisher.Flux;

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

    /**
     * Streams the model's message content as deltas. Implementations must not
     * log the API key or request payload containing personal data; runtime
     * provider failures are wrapped in {@link IllegalStateException} on the
     * resulting flux.
     */
    Flux<String> stream(String systemPrompt, List<ChatTurn> history, String userPrompt);

    /**
     * Structured chat: asks the model for JSON matching {@code type} and
     * converts it with the structured output converter. Throws RuntimeException
     * on provider or parse failure so callers can fall back.
     */
    <T> T chatStructured(String systemPrompt, String userPrompt, Class<T> type);

    record ChatTurn(String role, String content) {
    }
}
