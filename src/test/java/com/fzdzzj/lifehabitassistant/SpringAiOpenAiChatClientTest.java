package com.fzdzzj.lifehabitassistant;

import com.fzdzzj.lifehabitassistant.server.service.SpringAiOpenAiChatClient;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringAiOpenAiChatClientTest {
    private static final String SYSTEM_PROMPT = "system prompt";
    private static final String USER_PROMPT = "user prompt";

    private final ChatClient chatClient = mock(ChatClient.class);
    private final ChatClient.ChatClientRequestSpec request =
            mock(ChatClient.ChatClientRequestSpec.class);
    private final ChatClient.CallResponseSpec response =
            mock(ChatClient.CallResponseSpec.class);
    private final SpringAiOpenAiChatClient client = new SpringAiOpenAiChatClient(chatClient);

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
}
