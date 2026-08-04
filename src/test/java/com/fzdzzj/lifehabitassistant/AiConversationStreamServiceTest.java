package com.fzdzzj.lifehabitassistant;

import com.fzdzzj.lifehabitassistant.common.ApiException;
import com.fzdzzj.lifehabitassistant.pojo.AiConversationDtos;
import com.fzdzzj.lifehabitassistant.pojo.MessageSource;
import com.fzdzzj.lifehabitassistant.pojo.User;
import com.fzdzzj.lifehabitassistant.server.dao.AiConversationRepository;
import com.fzdzzj.lifehabitassistant.server.service.AiConversationService;
import com.fzdzzj.lifehabitassistant.server.service.AiConversationProperties;
import com.fzdzzj.lifehabitassistant.server.service.AiConversationStreamCoordinator;
import com.fzdzzj.lifehabitassistant.server.service.AiConversationStreamCoordinator.InFlightStream;
import com.fzdzzj.lifehabitassistant.server.service.AiConversationStreamService;
import com.fzdzzj.lifehabitassistant.server.service.CurrentUser;
import com.fzdzzj.lifehabitassistant.server.service.OpenAiChatClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiConversationStreamServiceTest {
    private AiConversationStreamCoordinator coordinator;
    private AiConversationService conversationService;
    private AiConversationRepository conversations;
    private CurrentUser currentUser;
    private OpenAiChatClient chatClient;
    private User user;
    private InFlightStream generation;
    private AiConversationStreamService service;

    @BeforeEach
    void setUp() {
        coordinator = mock(AiConversationStreamCoordinator.class);
        conversationService = mock(AiConversationService.class);
        conversations = mock(AiConversationRepository.class);
        currentUser = mock(CurrentUser.class);
        chatClient = mock(OpenAiChatClient.class);
        user = mock(User.class);
        when(user.getId()).thenReturn(42L);
        when(currentUser.require()).thenReturn(user);

        generation = mock(InFlightStream.class);
        when(generation.conversationId()).thenReturn(1L);
        when(generation.user()).thenReturn(user);
        when(generation.modelName()).thenReturn("gpt-demo");
        when(generation.fallbackReply()).thenReturn("降级回复");
        when(generation.quotaCounted()).thenReturn(true);
        when(generation.isCancelled()).thenReturn(false);
        when(generation.isFinished()).thenReturn(false);
        when(generation.markFinished()).thenReturn(true);
        when(generation.cancelReason()).thenReturn("user cancel");
        when(coordinator.cancel(anyLong(), anyLong(), anyString())).thenReturn(Optional.empty());
        when(conversationService.completeStream(any(), any(), any(), anyString(), any(), anyBoolean()))
                .thenReturn(response());

        Executor direct = Runnable::run;
        service = new AiConversationStreamService(coordinator, conversationService, conversations,
                currentUser, chatClient, new AiConversationProperties(true, 7, 10, 2000, 300, "v1"),
                direct);
    }

    @Test
    void startShouldStreamDeltasAndPersistCompleteMessage() {
        AiConversationService.PreparedStream prepared = prepared(true);
        when(conversationService.prepareStream(eq(1L), any())).thenReturn(prepared);
        when(coordinator.create(eq(1L), eq(user), eq(300_000L), eq("gpt-demo"), eq("降级回复"),
                eq(true), any())).thenReturn(created(Optional.empty()));
        when(generation.answerText()).thenReturn("你好");
        when(chatClient.stream(anyString(), any(), anyString())).thenReturn(Flux.just("你", "好"));

        service.start(1L, new AiConversationDtos.SendMessageRequest("今天状态如何"));

        verify(conversationService).completeStream(eq(1L), eq(user), eq(MessageSource.AI),
                eq("你好"), eq("gpt-demo"), eq(true));
        verify(coordinator).sendEvent(eq(generation), eq("start"), any());
        verify(coordinator).sendEvent(eq(generation), eq("delta"), eq("你"));
        verify(coordinator).sendEvent(eq(generation), eq("delta"), eq("好"));
        verify(coordinator).sendEvent(eq(generation), eq("complete"), any());
        verify(coordinator).complete(generation);
    }

    @Test
    void startShouldPersistFallbackWhenQuotaNotReserved() {
        AiConversationService.PreparedStream prepared = prepared(false);
        when(conversationService.prepareStream(eq(1L), any())).thenReturn(prepared);
        when(coordinator.create(eq(1L), eq(user), eq(300_000L), eq(null), eq("降级回复"),
                eq(false), any())).thenReturn(created(Optional.empty()));

        service.start(1L, new AiConversationDtos.SendMessageRequest("你好"));

        verify(chatClient, never()).stream(any(), any(), any());
        verify(conversationService).completeStream(eq(1L), eq(user), eq(MessageSource.RULE_FALLBACK),
                eq("降级回复"), eq(null), eq(false));
        verify(coordinator).sendEvent(eq(generation), eq("fallback"), any());
        verify(coordinator).complete(generation);
    }

    @Test
    void modelErrorShouldPersistCountedFallback() {
        AiConversationService.PreparedStream prepared = prepared(true);
        when(conversationService.prepareStream(eq(1L), any())).thenReturn(prepared);
        when(coordinator.create(eq(1L), eq(user), eq(300_000L), eq("gpt-demo"), eq("降级回复"),
                eq(true), any())).thenReturn(created(Optional.empty()));
        when(chatClient.stream(anyString(), any(), anyString()))
                .thenReturn(Flux.error(new IllegalStateException("provider timeout")));

        service.start(1L, new AiConversationDtos.SendMessageRequest("你好"));

        verify(conversationService).completeStream(eq(1L), eq(user), eq(MessageSource.RULE_FALLBACK),
                eq("降级回复"), eq("gpt-demo"), eq(true));
        verify(coordinator).sendEvent(eq(generation), eq("fallback"), any());
        verify(coordinator).complete(generation);
    }

    @Test
    void cancelShouldFinalizePartialTextAsAiMessage() {
        when(coordinator.cancel(1L, 42L, "user cancel")).thenReturn(Optional.of(generation));
        when(generation.answerText()).thenReturn("部分回答");

        service.cancelAndFinish(1L);

        verify(conversationService).completeStream(eq(1L), eq(user), eq(MessageSource.AI),
                eq("部分回答"), eq("gpt-demo"), eq(true));
        verify(coordinator).sendEvent(eq(generation), eq("cancelled"), any());
        verify(coordinator).complete(generation);
    }

    @Test
    void cancelWithoutPartialTextShouldPersistFallback() {
        when(coordinator.cancel(1L, 42L, "user cancel")).thenReturn(Optional.of(generation));
        when(generation.answerText()).thenReturn("");

        service.cancelAndFinish(1L);

        verify(conversationService).completeStream(eq(1L), eq(user), eq(MessageSource.RULE_FALLBACK),
                eq("降级回复"), eq("gpt-demo"), eq(true));
        verify(coordinator).sendEvent(eq(generation), eq("cancelled"), any());
    }

    @Test
    void cancelWithoutActiveGenerationShouldReturn404ForMissingConversation() {
        when(coordinator.cancel(1L, 42L, "user cancel")).thenReturn(Optional.empty());
        when(conversations.findByIdAndUserId(1L, 42L)).thenReturn(Optional.empty());

        assertThrows(ApiException.class, () -> service.cancelAndFinish(1L));
        verify(conversationService, never()).completeStream(any(), any(), any(), anyString(),
                any(), anyBoolean());
    }

    @Test
    void cancelWithoutActiveGenerationShouldReturn409ForOwnedConversation() {
        when(coordinator.cancel(1L, 42L, "user cancel")).thenReturn(Optional.empty());
        when(conversations.findByIdAndUserId(1L, 42L)).thenReturn(Optional.of(mock(
                com.fzdzzj.lifehabitassistant.pojo.AiConversation.class)));

        ApiException ex = assertThrows(ApiException.class, () -> service.cancelAndFinish(1L));

        assertEquals(40900, ex.errorCode().code());
    }

    @Test
    void newStreamShouldFinalizeTheReplacedGeneration() {
        InFlightStream replaced = mock(InFlightStream.class);
        when(replaced.answerText()).thenReturn("旧回答");
        when(replaced.isCancelled()).thenReturn(true);
        when(replaced.markFinished()).thenReturn(true);
        when(replaced.conversationId()).thenReturn(1L);
        when(replaced.user()).thenReturn(user);
        when(replaced.modelName()).thenReturn("gpt-demo");
        when(replaced.fallbackReply()).thenReturn("降级回复");
        when(replaced.quotaCounted()).thenReturn(true);
        when(replaced.cancelReason()).thenReturn("replaced by new request");

        AiConversationService.PreparedStream prepared = prepared(true);
        when(conversationService.prepareStream(eq(1L), any())).thenReturn(prepared);
        when(coordinator.create(eq(1L), eq(user), eq(300_000L), eq("gpt-demo"), eq("降级回复"),
                eq(true), any())).thenReturn(created(Optional.of(replaced)));
        when(generation.answerText()).thenReturn("新回答");
        when(chatClient.stream(anyString(), any(), anyString())).thenReturn(Flux.just("新回答"));

        service.start(1L, new AiConversationDtos.SendMessageRequest("继续"));

        verify(conversationService).completeStream(eq(1L), eq(user), eq(MessageSource.AI),
                eq("旧回答"), eq("gpt-demo"), eq(true));
        verify(coordinator).sendEvent(eq(replaced), eq("cancelled"), any());
    }

    private AiConversationService.PreparedStream prepared(boolean callModel) {
        return new AiConversationService.PreparedStream(user, 1L,
                callModel ? "system" : null, List.of(),
                callModel ? "user prompt" : null, "降级回复",
                callModel ? "gpt-demo" : null, callModel);
    }

    private AiConversationStreamCoordinator.CreateResult created(
            Optional<InFlightStream> replaced) {
        return new AiConversationStreamCoordinator.CreateResult(generation, replaced);
    }

    private AiConversationDtos.SendMessageResponse response() {
        return new AiConversationDtos.SendMessageResponse(
                new AiConversationDtos.MessageResponse(1L, null, MessageSource.AI, "回答",
                        "gpt-demo", true, LocalDateTime.now()), 1, 3, 1, 30);
    }
}
