package com.fzdzzj.lifehabitassistant.server.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Spring AI based OpenAI Chat adapter.
 *
 * Replaces the hand-written RestClient implementation after upgrading to
 * Spring Boot 3.5 + Spring AI 1.1.x. The bean is assembled in AiAdviceConfig
 * from the existing app.ai.advice.* properties, so service code and tests that
 * depend on {@link OpenAiChatClient} remain unchanged.
 */
@Component
public class SpringAiOpenAiChatClient implements OpenAiChatClient {
    private final ChatClient chatClient;

    public SpringAiOpenAiChatClient(@Qualifier("aiAdviceChatClient") ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        try {
            String content = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();
            if (content == null || content.isBlank()) {
                throw new IllegalStateException("OpenAI response contains no message content");
            }
            return content;
        } catch (IllegalStateException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IllegalStateException("OpenAI request failed", e);
        }
    }
}
