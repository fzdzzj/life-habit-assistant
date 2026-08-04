package com.fzdzzj.lifehabitassistant.server.service;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

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
    private final ChatClient streamChatClient;

    public SpringAiOpenAiChatClient(@Qualifier("aiAdviceChatClient") ChatClient chatClient,
                                    @Qualifier("aiConversationStreamChatClient") ChatClient streamChatClient) {
        this.chatClient = chatClient;
        this.streamChatClient = streamChatClient;
    }

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        return chat(systemPrompt, List.of(), userPrompt);
    }

    @Override
    public String chat(String systemPrompt, List<ChatTurn> history, String userPrompt) {
        try {
            List<Message> messages = history.stream().map(this::toMessage).toList();
            ChatClient.ChatClientRequestSpec request = chatClient.prompt().system(systemPrompt);
            if (!messages.isEmpty()) {
                request = request.messages(messages);
            }
            String content = request.user(userPrompt).call().content();
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

    @Override
    public Flux<String> stream(String systemPrompt, List<ChatTurn> history, String userPrompt) {
        List<Message> messages = history.stream().map(this::toMessage).toList();
        ChatClient.ChatClientRequestSpec request = streamChatClient.prompt().system(systemPrompt);
        if (!messages.isEmpty()) {
            request = request.messages(messages);
        }
        return request.user(userPrompt).stream().content()
                .onErrorMap(RuntimeException.class,
                        e -> new IllegalStateException("OpenAI request failed", e));
    }

    @Override
    public <T> T chatStructured(String systemPrompt, String userPrompt, Class<T> type) {
        try {
            return chatClient.prompt().system(systemPrompt).user(userPrompt).call().entity(type);
        } catch (IllegalStateException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IllegalStateException("OpenAI request failed", e);
        }
    }

    private Message toMessage(ChatTurn turn) {
        return switch (turn.role()) {
            case "user" -> new UserMessage(turn.content());
            case "assistant" -> new AssistantMessage(turn.content());
            default -> throw new IllegalArgumentException("未知的对话角色: " + turn.role());
        };
    }
}
