package com.fzdzzj.lifehabitassistant;

import com.fzdzzj.lifehabitassistant.pojo.User;
import com.fzdzzj.lifehabitassistant.server.service.AiConversationStreamCoordinator;
import com.fzdzzj.lifehabitassistant.server.service.AiConversationStreamCoordinator.InFlightStream;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiConversationStreamCoordinatorTest {
    private final AiConversationStreamCoordinator coordinator = new AiConversationStreamCoordinator();
    private final User user = user(42L);
    private final User other = user(7L);
    private final AtomicInteger aborts = new AtomicInteger();

    @Test
    void createShouldRegisterGenerationAndCancelShouldFinalizeIt() {
        InFlightStream generation = create(1L, user);

        assertFalse(generation.isCancelled());
        Optional<InFlightStream> cancelled = coordinator.cancel(1L, 42L, "user cancel");

        assertTrue(cancelled.isPresent());
        assertSame(generation, cancelled.orElseThrow());
        assertTrue(generation.isCancelled());
        assertEquals("user cancel", generation.cancelReason());
        assertTrue(coordinator.shouldAbort(generation));
    }

    @Test
    void cancelShouldRejectAnotherUsersGeneration() {
        InFlightStream generation = create(1L, user);

        Optional<InFlightStream> cancelled = coordinator.cancel(1L, 7L, "steal");

        assertTrue(cancelled.isEmpty());
        assertFalse(generation.isCancelled());
    }

    @Test
    void createShouldCancelAndReportTheReplacedGeneration() {
        InFlightStream first = create(1L, user);
        first.append("部分回答");

        AiConversationStreamCoordinator.CreateResult result = createResult(1L, user);

        assertSame(first, result.replaced().orElseThrow());
        assertTrue(first.isCancelled());
        assertEquals("replaced by new request", first.cancelReason());
        assertEquals("部分回答", first.answerText());
    }

    @Test
    void appendShouldAccumulateThreadSafely() {
        InFlightStream generation = create(1L, user);

        generation.append("你");
        generation.append("好");

        assertEquals("你好", generation.answerText());
    }

    @Test
    void markFinishedShouldWinOnlyOnce() {
        InFlightStream generation = create(1L, user);

        assertTrue(generation.markFinished());
        assertFalse(generation.markFinished());
        assertTrue(generation.isFinished());
    }

    @Test
    void abortAndNotifyShouldInvokeFinalizerOnce() {
        InFlightStream generation = create(1L, user);

        assertTrue(generation.abortAndNotify("timeout"));
        assertFalse(generation.abortAndNotify("timeout"));

        assertEquals(1, aborts.get());
        assertTrue(generation.isCancelled());
        assertEquals("timeout", generation.cancelReason());
    }

    private InFlightStream create(Long conversationId, User owner) {
        return createResult(conversationId, owner).current();
    }

    private AiConversationStreamCoordinator.CreateResult createResult(Long conversationId, User owner) {
        return coordinator.create(conversationId, owner, 300_000L, "gpt-demo",
                "降级回复", true, generation -> aborts.incrementAndGet());
    }

    private static User user(Long id) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        return user;
    }

}
