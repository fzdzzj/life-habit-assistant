package com.fzdzzj.lifehabitassistant.server.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Conversation-specific AI settings. The API key/model/base URL still come
 * from the shared {@link AiAdviceProperties} block so both features reuse one
 * provider configuration and one quota bucket.
 */
@ConfigurationProperties(prefix = "app.ai.conversation")
public record AiConversationProperties(
        boolean enabled,
        int contextDays,
        int maxHistoryRounds,
        int maxMessageLength,
        int streamTimeoutSeconds,
        String promptVersion) {

    public AiConversationProperties {
        if (contextDays < 1 || contextDays > 366) {
            throw new IllegalArgumentException("app.ai.conversation.context-days must be between 1 and 366");
        }
        if (maxHistoryRounds < 1 || maxHistoryRounds > 50) {
            throw new IllegalArgumentException("app.ai.conversation.max-history-rounds must be between 1 and 50");
        }
        if (maxMessageLength < 1 || maxMessageLength > 10000) {
            throw new IllegalArgumentException("app.ai.conversation.max-message-length must be between 1 and 10000");
        }
        if (streamTimeoutSeconds < 1 || streamTimeoutSeconds > 3600) {
            throw new IllegalArgumentException("app.ai.conversation.stream-timeout-seconds must be between 1 and 3600");
        }
        if (promptVersion == null || promptVersion.isBlank()) {
            promptVersion = "v1";
        }
    }
}
