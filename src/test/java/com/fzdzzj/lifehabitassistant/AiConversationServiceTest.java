package com.fzdzzj.lifehabitassistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fzdzzj.lifehabitassistant.common.ApiException;
import com.fzdzzj.lifehabitassistant.config.PaginationProperties;
import com.fzdzzj.lifehabitassistant.pojo.*;
import com.fzdzzj.lifehabitassistant.server.dao.AiConversationMessageRepository;
import com.fzdzzj.lifehabitassistant.server.dao.AiConversationRepository;
import com.fzdzzj.lifehabitassistant.server.dao.AiQuotaUsageRepository;
import com.fzdzzj.lifehabitassistant.server.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiConversationServiceTest {
    private static final String AI_REPLY = "根据你的记录，建议固定就寝时间。";

    private AiConversationRepository conversations;
    private AiConversationMessageRepository messages;
    private AiQuotaUsageRepository quotaRepo;
    private CurrentUser currentUser;
    private HabitService habits;
    private HealthStatisticsService statistics;
    private RuleBasedAdviceGenerator ruleAdvice;
    private GoalService goals;
    private OpenAiChatClient chatClient;
    private AiConversationPromptLoader promptLoader;
    private User user;
    private AiConversation conversation;
    private AiAdviceProperties aiProperties;
    private AiConversationProperties conversationProperties;
    private AiConversationService service;

    @BeforeEach
    void setUp() {
        conversations = mock(AiConversationRepository.class);
        messages = mock(AiConversationMessageRepository.class);
        quotaRepo = mock(AiQuotaUsageRepository.class);
        currentUser = mock(CurrentUser.class);
        habits = mock(HabitService.class);
        statistics = mock(HealthStatisticsService.class);
        ruleAdvice = mock(RuleBasedAdviceGenerator.class);
        goals = mock(GoalService.class);
        chatClient = mock(OpenAiChatClient.class);
        promptLoader = mock(AiConversationPromptLoader.class);

        user = mock(User.class);
        when(user.getId()).thenReturn(42L);
        when(user.getUsername()).thenReturn("alice");
        when(user.getAiDailyLimit()).thenReturn(null);
        when(user.getAiMonthlyLimit()).thenReturn(null);
        when(currentUser.require()).thenReturn(user);

        conversation = mock(AiConversation.class);
        when(conversation.getId()).thenReturn(1L);
        when(conversations.findByIdAndUserId(1L, 42L)).thenReturn(Optional.of(conversation));
        when(conversations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(messages.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(quotaRepo.incrementIfBelowLimit(any(), any(), any(), anyInt(), any())).thenReturn(1);
        when(quotaRepo.findUsedCount(any(), any(), any())).thenReturn(Optional.of(1));

        when(goals.effective(user)).thenReturn(new DailyGoals(420, 540, 1500, 30, 3));
        when(habits.range(any(User.class), any(), any())).thenReturn(List.of());
        when(statistics.summarize(any(), any(), any())).thenReturn(summary());
        when(ruleAdvice.generate(anyInt(), any(), any())).thenReturn(
                new AnalysisDtos.AnalysisResponse(7, 10, "已分析 10 条记录",
                        List.of("平均睡眠不足"), List.of("固定就寝时间")));

        aiProperties = new AiAdviceProperties(true, "sk-test", "gpt-demo",
                "https://api.openai.com/v1", 3, 30, 30, "v1");
        conversationProperties = new AiConversationProperties(true, 7, 10, 2000, 300, "v1");
        service = service(aiProperties, conversationProperties);
    }

    @Test
    void createShouldPersistOwnedConversationWithTrimmedTitle() {
        AiConversationDtos.ConversationResponse response =
                service.create(new AiConversationDtos.CreateConversationRequest("  我的对话  "));

        ArgumentCaptor<AiConversation> saved = ArgumentCaptor.forClass(AiConversation.class);
        verify(conversations).save(saved.capture());
        assertEquals(user, saved.getValue().getUser());
        assertEquals("我的对话", saved.getValue().getTitle());
        assertEquals("我的对话", response.title());
    }

    @Test
    void blankTitleShouldBeStoredAsNull() {
        service.create(new AiConversationDtos.CreateConversationRequest("   "));

        ArgumentCaptor<AiConversation> saved = ArgumentCaptor.forClass(AiConversation.class);
        verify(conversations).save(saved.capture());
        assertNull(saved.getValue().getTitle());
    }

    @Test
    void listShouldBeUserScopedAndOrderedByLastActivity() {
        when(conversations.findByUserId(eq(42L), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(conversation)));

        PageResponse<AiConversationDtos.ConversationResponse> result = service.list(0, 20);

        assertEquals(1, result.content().size());
        assertEquals(1L, result.content().get(0).id());
        ArgumentCaptor<PageRequest> pageRequest = ArgumentCaptor.forClass(PageRequest.class);
        verify(conversations).findByUserId(eq(42L), pageRequest.capture());
        assertEquals(Sort.by("lastActivityAt").descending(), pageRequest.getValue().getSort());
    }

    @Test
    void deepPageShouldBeRejected() {
        when(conversations.findByUserId(any(), any())).thenReturn(new PageImpl<>(List.of()));

        assertThrows(IllegalArgumentException.class, () -> service.list(1000, 100));
        verify(conversations, never()).findByUserId(any(), any());
    }

    @Test
    void messagesShouldBelongToCurrentUser() {
        AiConversationMessage prior = new AiConversationMessage(conversation, ConversationRole.USER,
                null, "早前消息", null, false);
        when(messages.findByConversationIdOrderByIdAsc(1L)).thenReturn(List.of(prior));

        List<AiConversationDtos.MessageResponse> result = service.messages(1L);

        assertEquals(1, result.size());
        assertEquals("早前消息", result.get(0).content());
        assertEquals(ConversationRole.USER, result.get(0).role());

        when(conversations.findByIdAndUserId(1L, 42L)).thenReturn(Optional.empty());
        assertThrows(ApiException.class, () -> service.messages(1L));
    }

    @Test
    void sendShouldSaveBothMessagesAndPassSanitizedContext() {
        List<AiConversationMessage> recent = List.of(
                new AiConversationMessage(conversation, ConversationRole.ASSISTANT, MessageSource.AI,
                        "之前的回答", "gpt-demo", true),
                new AiConversationMessage(conversation, ConversationRole.USER, null, "之前的问题", null, false));
        when(messages.findByConversationIdOrderByIdDesc(eq(1L), any())).thenReturn(recent);
        when(chatClient.chat(any(), any(), any())).thenReturn(AI_REPLY);

        AiConversationDtos.SendMessageResponse response =
                service.send(1L, new AiConversationDtos.SendMessageRequest("我今天状态如何？"));

        assertEquals(MessageSource.AI, response.message().source());
        assertEquals(AI_REPLY, response.message().content());
        assertEquals("gpt-demo", response.message().modelName());
        assertTrue(response.message().callCounted());

        ArgumentCaptor<List<OpenAiChatClient.ChatTurn>> history = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> userPrompt = ArgumentCaptor.forClass(String.class);
        verify(chatClient).chat(any(), history.capture(), userPrompt.capture());
        assertEquals(List.of("user", "assistant"),
                history.getValue().stream().map(OpenAiChatClient.ChatTurn::role).toList());
        assertTrue(userPrompt.getValue().contains("脱敏健康聚合指标"));
        assertTrue(userPrompt.getValue().contains("我今天状态如何？"));
        assertFalse(userPrompt.getValue().contains("alice"));
        assertFalse(userPrompt.getValue().contains("备注"));

        ArgumentCaptor<AiConversationMessage> saved = ArgumentCaptor.forClass(AiConversationMessage.class);
        verify(messages, times(2)).save(saved.capture());
        assertEquals(List.of(ConversationRole.USER, ConversationRole.ASSISTANT),
                saved.getAllValues().stream().map(AiConversationMessage::getRole).toList());
        verify(quotaRepo).incrementIfBelowLimit(eq(42L), eq("DAY"), any(), eq(3), any());
        verify(quotaRepo).incrementIfBelowLimit(eq(42L), eq("MONTH"), any(), eq(30), any());
    }

    @Test
    void sendShouldLimitHistoryToConfiguredRounds() {
        conversationProperties = new AiConversationProperties(true, 7, 2, 2000, 300, "v1");
        service = service(aiProperties, conversationProperties);
        List<AiConversationMessage> six = List.of(
                message(ConversationRole.ASSISTANT), message(ConversationRole.USER),
                message(ConversationRole.ASSISTANT), message(ConversationRole.USER),
                message(ConversationRole.ASSISTANT), message(ConversationRole.USER));
        when(messages.findByConversationIdOrderByIdDesc(eq(1L), any())).thenReturn(six);
        when(chatClient.chat(any(), any(), any())).thenReturn(AI_REPLY);

        service.send(1L, new AiConversationDtos.SendMessageRequest("继续"));

        ArgumentCaptor<List<OpenAiChatClient.ChatTurn>> history = ArgumentCaptor.forClass(List.class);
        verify(chatClient).chat(any(), history.capture(), any());
        assertEquals(4, history.getValue().size());
        assertEquals(List.of("user", "assistant", "user", "assistant"),
                history.getValue().stream().map(OpenAiChatClient.ChatTurn::role).toList());
    }

    @Test
    void sendShouldFallbackAndCountAttemptWhenProviderFails() {
        when(messages.findByConversationIdOrderByIdDesc(any(), any())).thenReturn(List.of());
        when(chatClient.chat(any(), any(), any()))
                .thenThrow(new IllegalStateException("provider timeout"));

        AiConversationDtos.SendMessageResponse response =
                service.send(1L, new AiConversationDtos.SendMessageRequest("怎么了"));

        assertEquals(MessageSource.RULE_FALLBACK, response.message().source());
        assertEquals("gpt-demo", response.message().modelName());
        assertTrue(response.message().callCounted());
        assertTrue(response.message().content().contains("本地规则"));
        verify(quotaRepo).incrementIfBelowLimit(eq(42L), eq("DAY"), any(), eq(3), any());
    }

    @Test
    void sendShouldFallbackWithoutCountingWhenDailyQuotaExhausted() {
        when(quotaRepo.incrementIfBelowLimit(any(), eq("DAY"), any(), anyInt(), any())).thenReturn(0);
        when(messages.findByConversationIdOrderByIdDesc(any(), any())).thenReturn(List.of());

        AiConversationDtos.SendMessageResponse response =
                service.send(1L, new AiConversationDtos.SendMessageRequest("帮我看看"));

        assertEquals(MessageSource.RULE_FALLBACK, response.message().source());
        assertFalse(response.message().callCounted());
        assertNull(response.message().modelName());
        verifyNoInteractions(chatClient);
        verify(quotaRepo, never()).incrementIfBelowLimit(any(), eq("MONTH"), any(), anyInt(), any());
    }

    @Test
    void sendShouldFallbackWithoutCallingProviderWhenDisabled() {
        conversationProperties = new AiConversationProperties(false, 7, 10, 2000, 300, "v1");
        service = service(aiProperties, conversationProperties);
        when(messages.findByConversationIdOrderByIdDesc(any(), any())).thenReturn(List.of());

        AiConversationDtos.SendMessageResponse response =
                service.send(1L, new AiConversationDtos.SendMessageRequest("测试"));

        assertEquals(MessageSource.RULE_FALLBACK, response.message().source());
        assertFalse(response.message().callCounted());
        verifyNoInteractions(chatClient);
        verify(quotaRepo, never()).incrementIfBelowLimit(any(), any(), any(), anyInt(), any());
    }

    @Test
    void sendShouldRejectOtherUsersConversationWithoutSaving() {
        when(conversations.findByIdAndUserId(1L, 42L)).thenReturn(Optional.empty());

        assertThrows(ApiException.class, () ->
                service.send(1L, new AiConversationDtos.SendMessageRequest("测试")));
        verify(messages, never()).save(any());
    }

    @Test
    void sendShouldRejectOversizedMessage() {
        conversationProperties = new AiConversationProperties(true, 7, 10, 10, 300, "v1");
        service = service(aiProperties, conversationProperties);

        assertThrows(ApiException.class, () ->
                service.send(1L, new AiConversationDtos.SendMessageRequest("超过十个字符的消息内容")));
        verify(messages, never()).save(any());
    }

    @Test
    void perUserQuotaOverrideShouldApplyToConversation() {
        when(user.getAiDailyLimit()).thenReturn(5);
        when(user.getAiMonthlyLimit()).thenReturn(60);
        when(messages.findByConversationIdOrderByIdDesc(any(), any())).thenReturn(List.of());
        when(chatClient.chat(any(), any(), any())).thenReturn(AI_REPLY);

        AiConversationDtos.SendMessageResponse response =
                service.send(1L, new AiConversationDtos.SendMessageRequest("继续"));

        assertEquals(5, response.dailyLimit());
        assertEquals(60, response.monthlyLimit());
        verify(quotaRepo).incrementIfBelowLimit(eq(42L), eq("DAY"), any(), eq(5), any());
        verify(quotaRepo).incrementIfBelowLimit(eq(42L), eq("MONTH"), any(), eq(60), any());
    }

    @Test
    void deleteShouldRemoveMessagesThenConversation() {
        service.delete(1L);

        verify(messages).deleteByConversationId(1L);
        verify(conversations).deleteById(1L);
    }

    @Test
    void deleteShouldRejectOtherUsersConversation() {
        when(conversations.findByIdAndUserId(1L, 42L)).thenReturn(Optional.empty());

        assertThrows(ApiException.class, () -> service.delete(1L));
        verify(messages, never()).deleteByConversationId(anyLong());
        verify(conversations, never()).deleteById(anyLong());
    }

    private AiConversationService service(AiAdviceProperties ai, AiConversationProperties conversation) {
        return new AiConversationService(conversations, messages, currentUser, habits, statistics,
                ruleAdvice, goals, new AiQuotaService(quotaRepo, ai), ai, conversation,
                promptLoader, chatClient, new PaginationProperties(10000), new ObjectMapper());
    }

    private AiConversationMessage message(ConversationRole role) {
        return new AiConversationMessage(conversation, role,
                role == ConversationRole.ASSISTANT ? MessageSource.AI : null,
                role == ConversationRole.USER ? "问题" : "回答", "gpt-demo",
                role == ConversationRole.ASSISTANT);
    }

    private HealthStatistics summary() {
        return new HealthStatistics(10, 7.5, 4.0, 300, 350, 3,
                1600.0, 0, 5, List.of(), java.util.Map.of(), java.util.Map.of());
    }
}
