package com.fzdzzj.lifehabitassistant.server.service;

import com.fzdzzj.lifehabitassistant.common.ApiException;
import com.fzdzzj.lifehabitassistant.pojo.AiConversationDtos;
import com.fzdzzj.lifehabitassistant.pojo.MessageSource;
import com.fzdzzj.lifehabitassistant.pojo.User;
import com.fzdzzj.lifehabitassistant.server.dao.AiConversationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * Orchestrates SSE streaming for AI conversations.
 *
 * The request thread owns the SecurityContext, so the transactional prepare
 * (validate + persist user message + reserve quota) runs synchronously. The
 * model subscription then runs on the AI stream executor; finalization saves
 * the assistant message in a fresh transaction through
 * {@link AiConversationService}.
 */
@Service
public class AiConversationStreamService {
    private static final Logger log = LoggerFactory.getLogger(AiConversationStreamService.class);

    private final AiConversationStreamCoordinator coordinator;
    private final AiConversationService conversationService;
    private final AiConversationRepository conversations;
    private final CurrentUser currentUser;
    private final OpenAiChatClient chatClient;
    private final AiConversationProperties properties;
    private final Executor aiStreamExecutor;

    public AiConversationStreamService(AiConversationStreamCoordinator coordinator,
                                       AiConversationService conversationService,
                                       AiConversationRepository conversations,
                                       CurrentUser currentUser,
                                       OpenAiChatClient chatClient,
                                       AiConversationProperties properties,
                                       @Qualifier("aiStreamExecutor") Executor aiStreamExecutor) {
        this.coordinator = coordinator;
        this.conversationService = conversationService;
        this.conversations = conversations;
        this.currentUser = currentUser;
        this.chatClient = chatClient;
        this.properties = properties;
        this.aiStreamExecutor = aiStreamExecutor;
    }

    /**
     * Starts an SSE generation for the current user. Any previous active
     * generation of the same conversation is cancelled and finalized first, so
     * one conversation never has two in-flight model calls.
     */
    public SseEmitter start(Long conversationId, AiConversationDtos.SendMessageRequest request) {
        User user = currentUser.require();
        cancelActive(conversationId, user.getId(), "replaced by new request");

        AiConversationService.PreparedStream prepared = conversationService.prepareStream(conversationId, request);
        AiConversationStreamCoordinator.CreateResult result = coordinator.create(conversationId, user,
                properties.streamTimeoutSeconds() * 1000L, prepared.modelName(), prepared.fallbackReply(),
                prepared.callModel(), this::finishIfNeeded);
        result.replaced().ifPresent(this::finishIfNeeded);

        coordinator.sendEvent(result.current(), "start", Map.of("conversationId", conversationId));
        aiStreamExecutor.execute(() -> runStream(result.current(), prepared));
        return result.current().emitter();
    }

    /**
     * Cancels and finalizes the active generation. Used by the cancel endpoint
     * and by sync sends (a sync send supersedes an old stream).
     */
    public void cancelActiveForSyncSend(Long conversationId) {
        User user = currentUser.require();
        cancelActive(conversationId, user.getId(), "superseded by sync send");
    }

    /**
     * Cancel endpoint: cancels the active generation or returns 404/409.
     */
    public void cancelAndFinish(Long conversationId) {
        User user = currentUser.require();
        Optional<AiConversationStreamCoordinator.InFlightStream> generation =
                coordinator.cancel(conversationId, user.getId(), "user cancel");
        if (generation.isEmpty()) {
            if (conversations.findByIdAndUserId(conversationId, user.getId()).isEmpty()) {
                throw ApiException.notFound("对话不存在");
            }
            throw ApiException.conflict("该会话当前没有进行中的生成任务");
        }
        finishIfNeeded(generation.get());
    }

    private void cancelActive(Long conversationId, Long userId, String reason) {
        coordinator.cancel(conversationId, userId, reason).ifPresent(this::finishIfNeeded);
    }

    private void runStream(AiConversationStreamCoordinator.InFlightStream generation,
                           AiConversationService.PreparedStream prepared) {
        try {
            if (coordinator.shouldAbort(generation)) {
                finishIfNeeded(generation);
                return;
            }
            if (!prepared.callModel()) {
                if (!generation.markFinished()) {
                    return;
                }
                AiConversationDtos.SendMessageResponse response = conversationService.completeStream(
                        generation.conversationId(), generation.user(), MessageSource.RULE_FALLBACK,
                        prepared.fallbackReply(), null, false);
                coordinator.sendEvent(generation, "fallback", response);
                coordinator.complete(generation);
                return;
            }

            Flux<String> flux = chatClient.stream(prepared.systemPrompt(), prepared.history(),
                    prepared.userPrompt());
            generation.captureDisposable(flux.subscribe(
                    delta -> {
                        if (coordinator.shouldAbort(generation)) {
                            return;
                        }
                        generation.append(delta);
                        coordinator.sendEvent(generation, "delta", delta);
                    },
                    error -> handleModelError(generation, prepared, error),
                    () -> handleModelComplete(generation, prepared)
            ));
        } catch (RuntimeException ex) {
            handleModelError(generation, prepared, ex);
        }
    }

    private void handleModelComplete(AiConversationStreamCoordinator.InFlightStream generation,
                                     AiConversationService.PreparedStream prepared) {
        if (coordinator.shouldAbort(generation)) {
            finishIfNeeded(generation);
            return;
        }
        if (!generation.markFinished()) {
            return;
        }
        AiConversationDtos.SendMessageResponse response = conversationService.completeStream(
                generation.conversationId(), generation.user(), MessageSource.AI,
                generation.answerText(), generation.modelName(), true);
        coordinator.sendEvent(generation, "complete", response);
        coordinator.complete(generation);
    }

    private void handleModelError(AiConversationStreamCoordinator.InFlightStream generation,
                                  AiConversationService.PreparedStream prepared, Throwable error) {
        if (generation.isCancelled()) {
            finishIfNeeded(generation);
            return;
        }
        if (!generation.markFinished()) {
            return;
        }
        log.warn("OpenAI stream failed for user {}: {}", generation.user().getId(), error.toString());
        AiConversationDtos.SendMessageResponse response = conversationService.completeStream(
                generation.conversationId(), generation.user(), MessageSource.RULE_FALLBACK,
                prepared.fallbackReply(), generation.modelName(), true);
        coordinator.sendEvent(generation, "fallback", response);
        coordinator.complete(generation);
    }

    /**
     * Persists whatever the stream produced before cancellation/timeout/client
     * disconnect: non-blank partial text becomes an AI message, otherwise a
     * rule fallback is saved. Only the first caller wins per generation.
     */
    private void finishIfNeeded(AiConversationStreamCoordinator.InFlightStream generation) {
        if (!generation.markFinished()) {
            return;
        }
        String text = generation.answerText();
        AiConversationDtos.SendMessageResponse response;
        if (text.isBlank()) {
            response = conversationService.completeStream(generation.conversationId(), generation.user(),
                    MessageSource.RULE_FALLBACK, generation.fallbackReply(), generation.modelName(),
                    generation.quotaCounted());
        } else {
            response = conversationService.completeStream(generation.conversationId(), generation.user(),
                    MessageSource.AI, text, generation.modelName(), generation.quotaCounted());
        }
        coordinator.sendEvent(generation, "cancelled", Map.of(
                "conversationId", generation.conversationId(),
                "reason", generation.cancelReason(),
                "message", response));
        coordinator.complete(generation);
    }
}
