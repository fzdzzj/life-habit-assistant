package com.fzdzzj.lifehabitassistant.server.service;

import com.fzdzzj.lifehabitassistant.pojo.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Per-conversation in-flight streaming coordinator.
 *
 * One conversation can have at most one active generation: a new stream or a
 * synchronous send cancels and finalizes the previous one. The state machine
 * mirrors the reference RAG design (D:\code\rag\back\RAG) with the same
 * delta/complete/fallback/error/cancelled event semantics, adapted to the
 * Spring AI Flux API and this project's persistence model.
 */
@Component
public class AiConversationStreamCoordinator {
    private static final Logger log = LoggerFactory.getLogger(AiConversationStreamCoordinator.class);

    private final ConcurrentHashMap<Long, InFlightStream> inFlight = new ConcurrentHashMap<>();

    /**
     * Creates a new stream session, cancelling any previous active generation
     * for the same conversation. The replaced generation (if any) is returned
     * so the caller can persist its partial answer.
     */
    public CreateResult create(Long conversationId, User user, long timeoutMs, String modelName,
                               String fallbackReply, boolean quotaCounted,
                               Consumer<InFlightStream> onAbort) {
        SseEmitter emitter = new SseEmitter(timeoutMs);
        InFlightStream generation = new InFlightStream(conversationId, user, emitter, modelName,
                fallbackReply, quotaCounted, onAbort);
        emitter.onTimeout(() -> generation.abortAndNotify("timeout"));
        emitter.onError(error -> generation.abortAndNotify("client disconnect"));
        emitter.onCompletion(generation::disposeQuietly);

        AtomicReference<InFlightStream> replaced = new AtomicReference<>();
        inFlight.compute(conversationId, (key, previous) -> {
            if (previous != null && previous.cancelAndDispose("replaced by new request")) {
                replaced.set(previous);
            }
            return generation;
        });
        return new CreateResult(generation, Optional.ofNullable(replaced.get()));
    }

    /**
     * Cancels the active generation of a conversation owned by {@code userId}.
     * Returns the cancelled generation so the caller can finalize it, or empty
     * when there is no matching active task.
     */
    public Optional<InFlightStream> cancel(Long conversationId, Long userId, String reason) {
        InFlightStream generation = inFlight.get(conversationId);
        if (generation == null || !generation.user().getId().equals(userId)) {
            return Optional.empty();
        }
        generation.cancelAndDispose(reason);
        return Optional.of(generation);
    }

    /**
     * Sends an SSE event unless the generation was cancelled or finished.
     * Send failures (typically a dropped client) abort and finalize the task.
     */
    public void sendEvent(InFlightStream generation, String name, Object data) {
        if (generation.isCancelled() && !"cancelled".equals(name)) {
            return;
        }
        try {
            generation.emitter().send(SseEmitter.event().name(name).data(data));
        } catch (IOException e) {
            log.warn("SSE send failed: conversationId={}, event={}, error={}",
                    generation.conversationId(), name, e.toString());
            generation.abortAndNotify("send failed");
        }
    }

    /**
     * Completes the emitter and removes the session from the registry.
     */
    public void complete(InFlightStream generation) {
        generation.disposeQuietly();
        inFlight.computeIfPresent(generation.conversationId(),
                (key, current) -> current == generation ? null : current);
        try {
            generation.emitter().complete();
        } catch (Exception e) {
            log.debug("SSE complete ignored: conversationId={}, message={}",
                    generation.conversationId(), e.getMessage());
        }
    }

    public boolean shouldAbort(InFlightStream generation) {
        return generation.isCancelled() || generation.isFinished();
    }

    public record CreateResult(InFlightStream current, Optional<InFlightStream> replaced) {
    }

    /**
     * Mutable state of one streaming generation. All state transitions are
     * atomic so the request thread, stream thread and cancel endpoint cannot
     * race each other.
     */
    public static final class InFlightStream {
        private final Long conversationId;
        private final User user;
        private final SseEmitter emitter;
        private final String modelName;
        private final String fallbackReply;
        private final boolean quotaCounted;
        private final Consumer<InFlightStream> onAbort;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final AtomicBoolean finished = new AtomicBoolean(false);
        private final AtomicReference<String> cancelReason = new AtomicReference<>();
        private final AtomicReference<Disposable> disposable = new AtomicReference<>();
        private final StringBuilder answer = new StringBuilder();

        private InFlightStream(Long conversationId, User user, SseEmitter emitter, String modelName,
                               String fallbackReply, boolean quotaCounted,
                               Consumer<InFlightStream> onAbort) {
            this.conversationId = conversationId;
            this.user = user;
            this.emitter = emitter;
            this.modelName = modelName;
            this.fallbackReply = fallbackReply;
            this.quotaCounted = quotaCounted;
            this.onAbort = onAbort;
        }

        public Long conversationId() {
            return conversationId;
        }

        public User user() {
            return user;
        }

        public SseEmitter emitter() {
            return emitter;
        }

        public String modelName() {
            return modelName;
        }

        public String fallbackReply() {
            return fallbackReply;
        }

        public boolean quotaCounted() {
            return quotaCounted;
        }

        public boolean isCancelled() {
            return cancelled.get();
        }

        public boolean isFinished() {
            return finished.get();
        }

        public String cancelReason() {
            return cancelReason.get();
        }

        /**
         * Marks the task cancelled and disposes the model subscription.
         * Returns false when already cancelled.
         */
        public boolean cancelAndDispose(String reason) {
            if (!cancelled.compareAndSet(false, true)) {
                return false;
            }
            cancelReason.compareAndSet(null, reason);
            disposeQuietly();
            return true;
        }

        /**
         * Cancels, disposes and notifies the registered finalizer. Used by
         * timeouts, client disconnects and send failures.
         */
        public boolean abortAndNotify(String reason) {
            if (!cancelAndDispose(reason)) {
                return false;
            }
            onAbort.accept(this);
            return true;
        }

        /**
         * Marks the task finished. Only the first caller wins, so cancellation
         * finalization and stream completion cannot persist two answers.
         */
        public boolean markFinished() {
            return finished.compareAndSet(false, true);
        }

        public void captureDisposable(Disposable subscription) {
            if (subscription == null) {
                return;
            }
            disposable.set(subscription);
            if (isCancelled() || isFinished()) {
                subscription.dispose();
            }
        }

        public void disposeQuietly() {
            Disposable subscription = disposable.get();
            if (subscription != null && !subscription.isDisposed()) {
                subscription.dispose();
            }
        }

        public void append(String text) {
            if (text == null) {
                return;
            }
            synchronized (answer) {
                answer.append(text);
            }
        }

        public String answerText() {
            synchronized (answer) {
                return answer.toString();
            }
        }
    }
}
