package com.fzdzzj.lifehabitassistant.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fzdzzj.lifehabitassistant.common.ApiException;
import com.fzdzzj.lifehabitassistant.config.PaginationProperties;
import com.fzdzzj.lifehabitassistant.pojo.AiConversation;
import com.fzdzzj.lifehabitassistant.pojo.AiConversationDtos;
import com.fzdzzj.lifehabitassistant.pojo.AiConversationMessage;
import com.fzdzzj.lifehabitassistant.pojo.AnalysisDtos;
import com.fzdzzj.lifehabitassistant.pojo.ConversationRole;
import com.fzdzzj.lifehabitassistant.pojo.DailyGoals;
import com.fzdzzj.lifehabitassistant.pojo.HealthStatistics;
import com.fzdzzj.lifehabitassistant.pojo.MessageSource;
import com.fzdzzj.lifehabitassistant.pojo.PageResponse;
import com.fzdzzj.lifehabitassistant.pojo.User;
import com.fzdzzj.lifehabitassistant.server.dao.AiConversationMessageRepository;
import com.fzdzzj.lifehabitassistant.server.dao.AiConversationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiConversationService {
    private static final Logger log = LoggerFactory.getLogger(AiConversationService.class);
    private static final String DISCLAIMER =
            "本建议仅作健康生活方式参考，不构成医疗诊断或治疗建议；如有健康问题请咨询医生。";

    private final AiConversationRepository conversations;
    private final AiConversationMessageRepository messages;
    private final CurrentUser currentUser;
    private final HabitService habits;
    private final HealthStatisticsService statistics;
    private final RuleBasedAdviceGenerator ruleAdvice;
    private final GoalService goals;
    private final AiQuotaService quotaService;
    private final AiAdviceProperties aiProperties;
    private final AiConversationProperties conversationProperties;
    private final AiConversationPromptLoader promptLoader;
    private final OpenAiChatClient chatClient;
    private final PaginationProperties pagination;
    private final ObjectMapper objectMapper;

    public AiConversationService(AiConversationRepository conversations,
                                 AiConversationMessageRepository messages,
                                 CurrentUser currentUser, HabitService habits,
                                 HealthStatisticsService statistics, RuleBasedAdviceGenerator ruleAdvice,
                                 GoalService goals, AiQuotaService quotaService,
                                 AiAdviceProperties aiProperties,
                                 AiConversationProperties conversationProperties,
                                 AiConversationPromptLoader promptLoader, OpenAiChatClient chatClient,
                                 PaginationProperties pagination, ObjectMapper objectMapper) {
        this.conversations = conversations;
        this.messages = messages;
        this.currentUser = currentUser;
        this.habits = habits;
        this.statistics = statistics;
        this.ruleAdvice = ruleAdvice;
        this.goals = goals;
        this.quotaService = quotaService;
        this.aiProperties = aiProperties;
        this.conversationProperties = conversationProperties;
        this.promptLoader = promptLoader;
        this.chatClient = chatClient;
        this.pagination = pagination;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AiConversationDtos.ConversationResponse create(AiConversationDtos.CreateConversationRequest request) {
        User user = currentUser.require();
        String title = request == null || request.title() == null ? null : request.title().trim();
        if (title != null && title.isBlank()) {
            title = null;
        }
        return toResponse(conversations.save(new AiConversation(user, title)));
    }

    @Transactional(readOnly = true)
    public PageResponse<AiConversationDtos.ConversationResponse> list(int page, int size) {
        User user = currentUser.require();
        long offset = (long) page * size;
        if (offset >= pagination.maxOffset()) {
            throw new IllegalArgumentException("页码过深（offset 不得超过 " + pagination.maxOffset() + "）");
        }
        Page<AiConversation> result = conversations.findByUserId(user.getId(),
                PageRequest.of(page, size, Sort.by("lastActivityAt").descending()));
        return PageResponse.from(result.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public List<AiConversationDtos.MessageResponse> messages(Long conversationId) {
        User user = currentUser.require();
        requireOwned(conversationId, user);
        return messages.findByConversationIdOrderByIdAsc(conversationId).stream()
                .map(this::toMessageResponse)
                .toList();
    }

    @Transactional
    public AiConversationDtos.SendMessageResponse send(Long conversationId,
                                                       AiConversationDtos.SendMessageRequest request) {
        User user = currentUser.require();
        AiConversation conversation = requireOwned(conversationId, user);
        String content = request.content().trim();
        if (content.length() > conversationProperties.maxMessageLength()) {
            throw ApiException.badRequest("消息长度不得超过 " + conversationProperties.maxMessageLength() + " 字符");
        }

        SanitizedContext context = buildContext(user);
        List<AiConversationMessage> recent = recentMessages(conversationId,
                conversationProperties.maxHistoryRounds() * 2);
        saveMessage(conversation, ConversationRole.USER, null, content, null, false);
        conversation.touch();
        conversations.save(conversation);

        if (!eligible()) {
            return reply(user, conversation, MessageSource.RULE_FALLBACK,
                    fallbackReply(context.rule()), null, false);
        }
        try {
            quotaService.occupy(user);
        } catch (AiQuotaService.QuotaExceededException ex) {
            return reply(user, conversation, MessageSource.RULE_FALLBACK,
                    fallbackReply(context.rule()), null, false);
        }
        try {
            String raw = chatClient.chat(promptLoader.load(), turns(recent), userPrompt(context, content));
            return reply(user, conversation, MessageSource.AI, raw, aiProperties.model(), true);
        } catch (RuntimeException ex) {
            log.warn("OpenAI conversation failed for user {}: {}", user.getId(), ex.toString());
            return reply(user, conversation, MessageSource.RULE_FALLBACK,
                    fallbackReply(context.rule()), aiProperties.model(), true);
        }
    }

    @Transactional
    public void delete(Long conversationId) {
        User user = currentUser.require();
        requireOwned(conversationId, user);
        messages.deleteByConversationId(conversationId);
        conversations.deleteById(conversationId);
    }

    private AiConversationDtos.SendMessageResponse reply(User user, AiConversation conversation,
                                                         MessageSource source, String content,
                                                         String modelName, boolean callCounted) {
        AiConversationMessage saved = saveMessage(conversation, ConversationRole.ASSISTANT, source,
                content, modelName, callCounted);
        AiQuotaService.QuotaSnapshot quota = quotaService.usage(user);
        return new AiConversationDtos.SendMessageResponse(toMessageResponse(saved),
                quota.dailyUsed(), quota.dailyLimit(), quota.monthlyUsed(), quota.monthlyLimit());
    }

    private AiConversationMessage saveMessage(AiConversation conversation, ConversationRole role,
                                              MessageSource source, String content, String modelName,
                                              boolean callCounted) {
        return messages.save(new AiConversationMessage(conversation, role, source, content, modelName,
                callCounted));
    }

    private List<AiConversationMessage> recentMessages(Long conversationId, int limit) {
        List<AiConversationMessage> desc = messages.findByConversationIdOrderByIdDesc(conversationId,
                PageRequest.of(0, limit));
        if (desc.size() > limit) {
            desc = desc.subList(0, limit);
        }
        List<AiConversationMessage> asc = new ArrayList<>(desc);
        Collections.reverse(asc);
        return asc;
    }

    private List<OpenAiChatClient.ChatTurn> turns(List<AiConversationMessage> recent) {
        return recent.stream()
                .map(message -> new OpenAiChatClient.ChatTurn(
                        message.getRole() == ConversationRole.USER ? "user" : "assistant",
                        message.getContent()))
                .toList();
    }

    private SanitizedContext buildContext(User user) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(conversationProperties.contextDays() - 1L);
        DailyGoals effectiveGoals = goals.effective(user);
        HealthStatistics summary = statistics.summarize(habits.range(user, start, end), end, effectiveGoals);
        AnalysisDtos.AnalysisResponse rule = ruleAdvice.generate(
                conversationProperties.contextDays(), summary, effectiveGoals);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("days", conversationProperties.contextDays());
        payload.put("recordCount", summary.recordCount());
        payload.put("averageSleepHours", summary.averageSleepHours());
        payload.put("averageDietScore", summary.averageDietScore());
        payload.put("totalExerciseMinutes", summary.totalExerciseMinutes());
        payload.put("averageHydrationMl", summary.averageHydrationMl());
        payload.put("totalRiskDrinkVolumeMl", summary.totalRiskDrinkVolumeMl());
        payload.put("consecutiveDays", summary.consecutiveDays());
        payload.put("exerciseMinutesByType", summary.exerciseMinutesByType());
        payload.put("drinkVolumesByType", summary.drinkVolumesByType());
        payload.put("ruleRisks", rule.risks());
        payload.put("ruleSuggestions", rule.suggestions());
        payload.put("dailyGoals", effectiveGoals);
        return new SanitizedContext(rule, toJson(payload));
    }

    private String userPrompt(SanitizedContext context, String content) {
        return "以下是最近 " + conversationProperties.contextDays()
                + " 天的脱敏健康聚合指标与规则结论：\n" + context.json()
                + "\n\n用户最新消息：\n" + content;
    }

    private String fallbackReply(AnalysisDtos.AnalysisResponse rule) {
        StringBuilder reply = new StringBuilder("基于最近脱敏健康指标：").append(rule.summary());
        if (!rule.suggestions().isEmpty()) {
            reply.append("\n建议：").append(String.join("；", rule.suggestions()));
        }
        reply.append("\n\n（当前 AI 服务不可用或配额已满，本条由本地规则生成。）").append(DISCLAIMER);
        return reply.toString();
    }

    private boolean eligible() {
        return conversationProperties.enabled()
                && notBlank(aiProperties.apiKey())
                && notBlank(aiProperties.model());
    }

    private AiConversation requireOwned(Long conversationId, User user) {
        return conversations.findByIdAndUserId(conversationId, user.getId())
                .orElseThrow(() -> ApiException.notFound("对话不存在"));
    }

    private AiConversationDtos.ConversationResponse toResponse(AiConversation conversation) {
        return new AiConversationDtos.ConversationResponse(conversation.getId(), conversation.getTitle(),
                conversation.getCreatedAt(), conversation.getLastActivityAt());
    }

    private AiConversationDtos.MessageResponse toMessageResponse(AiConversationMessage message) {
        return new AiConversationDtos.MessageResponse(message.getId(), message.getRole(), message.getSource(),
                message.getContent(), message.getModelName(), message.isCallCounted(), message.getCreatedAt());
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("无法序列化对话上下文", e);
        }
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private record SanitizedContext(AnalysisDtos.AnalysisResponse rule, String json) {
    }
}
