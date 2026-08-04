package com.fzdzzj.lifehabitassistant;

import com.fzdzzj.lifehabitassistant.server.service.OpenAiChatClient;
import com.fzdzzj.lifehabitassistant.server.service.SpringAiOpenAiChatClient;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.CallResponseSpec;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.client.ChatClient.StreamResponseSpec;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import reactor.core.publisher.Flux;

class SpringAiOpenAiChatClientTest {
    private static final String SYSTEM_PROMPT = "system prompt";
    private static final String USER_PROMPT = "user prompt";

    private final ChatClient chatClient = mock(ChatClient.class);
    private final ChatClient streamChatClient = mock(ChatClient.class);
    private final ChatClientRequestSpec request = mock(ChatClientRequestSpec.class);
    private final CallResponseSpec response = mock(CallResponseSpec.class);
    private final SpringAiOpenAiChatClient client =
            new SpringAiOpenAiChatClient(chatClient, streamChatClient);

    @Test
    void delegatesSystemAndUserMessagesAndReturnsContent() {
        when(chatClient.prompt()).thenReturn(request);
        when(request.system(SYSTEM_PROMPT)).thenReturn(request);
        when(request.user(USER_PROMPT)).thenReturn(request);
        when(request.call()).thenReturn(response);
        when(response.content()).thenReturn("AI answer");

        assertEquals("AI answer", client.chat(SYSTEM_PROMPT, USER_PROMPT));
        verify(request).system(SYSTEM_PROMPT);
        verify(request).user(USER_PROMPT);
    }

    @Test
    void rejectsBlankContent() {
        when(chatClient.prompt()).thenReturn(request);
        when(request.system(SYSTEM_PROMPT)).thenReturn(request);
        when(request.user(USER_PROMPT)).thenReturn(request);
        when(request.call()).thenReturn(response);
        when(response.content()).thenReturn("   ");

        assertThrows(IllegalStateException.class, () -> client.chat(SYSTEM_PROMPT, USER_PROMPT));
    }

    @Test
    void wrapsProviderFailure() {
        when(chatClient.prompt()).thenThrow(new RuntimeException("provider down"));

        IllegalStateException ex = assertThrows(
                IllegalStateException.class, () -> client.chat(SYSTEM_PROMPT, USER_PROMPT));
        assertEquals("OpenAI request failed", ex.getMessage());
        assertEquals("provider down", ex.getCause().getMessage());
    }

    @Test
    void multiTurnShouldDelegateHistoryAndLatestUserMessage() {
        when(chatClient.prompt()).thenReturn(request);
        when(request.system(SYSTEM_PROMPT)).thenReturn(request);
        when(request.messages(anyList())).thenReturn(request);
        when(request.user(USER_PROMPT)).thenReturn(request);
        when(request.call()).thenReturn(response);
        when(response.content()).thenReturn("AI answer");

        List<OpenAiChatClient.ChatTurn> history = List.of(
                new OpenAiChatClient.ChatTurn("user", "Q1"),
                new OpenAiChatClient.ChatTurn("assistant", "A1"));

        assertEquals("AI answer", client.chat(SYSTEM_PROMPT, history, USER_PROMPT));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Message>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(request).messages(messagesCaptor.capture());
        assertEquals(List.of(MessageType.USER, MessageType.ASSISTANT),
                messagesCaptor.getValue().stream().map(Message::getMessageType).toList());
        verify(request).user(USER_PROMPT);
    }

    @Test
    void streamShouldDelegateToStreamingClientAndReturnDeltas() {
        ChatClientRequestSpec streamRequest = mock(ChatClientRequestSpec.class);
        StreamResponseSpec streamResponse = mock(StreamResponseSpec.class);
        when(streamChatClient.prompt()).thenReturn(streamRequest);
        when(streamRequest.system(SYSTEM_PROMPT)).thenReturn(streamRequest);
        when(streamRequest.user(USER_PROMPT)).thenReturn(streamRequest);
        when(streamRequest.stream()).thenReturn(streamResponse);
        when(streamResponse.content()).thenReturn(Flux.just("你", "好"));

        AtomicReference<String> collected = new AtomicReference<>("");
        client.stream(SYSTEM_PROMPT, List.of(), USER_PROMPT)
                .doOnNext(text -> collected.accumulateAndGet(text, String::concat))
                .blockLast();

        assertEquals("你好", collected.get());
        verify(streamRequest).system(SYSTEM_PROMPT);
        verify(streamRequest).user(USER_PROMPT);
    }

    @Test
    void streamShouldWrapProviderFailure() {
        ChatClientRequestSpec streamRequest = mock(ChatClientRequestSpec.class);
        StreamResponseSpec streamResponse = mock(StreamResponseSpec.class);
        when(streamChatClient.prompt()).thenReturn(streamRequest);
        when(streamRequest.system(SYSTEM_PROMPT)).thenReturn(streamRequest);
        when(streamRequest.user(USER_PROMPT)).thenReturn(streamRequest);
        when(streamRequest.stream()).thenReturn(streamResponse);
        when(streamResponse.content()).thenReturn(Flux.error(new RuntimeException("provider down")));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> client.stream(SYSTEM_PROMPT, List.of(), USER_PROMPT).blockLast());
        assertEquals("OpenAI request failed", ex.getMessage());
        assertEquals("provider down", ex.getCause().getMessage());
    }

    @Test
    void chatStructuredShouldDelegateEntityConversion() {
        when(chatClient.prompt()).thenReturn(request);
        when(request.system(SYSTEM_PROMPT)).thenReturn(request);
        when(request.user(USER_PROMPT)).thenReturn(request);
        when(request.call()).thenReturn(response);
        when(response.entity(eq(String.class))).thenReturn("structured-value");

        assertEquals("structured-value", client.chatStructured(SYSTEM_PROMPT, USER_PROMPT, String.class));
        verify(request).system(SYSTEM_PROMPT);
        verify(request).user(USER_PROMPT);
        verify(response).entity(String.class);
    }
}
