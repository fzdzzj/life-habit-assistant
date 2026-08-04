package com.fzdzzj.lifehabitassistant.pojo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class AiConversationDtos {
    private AiConversationDtos() {
    }

    public record CreateConversationRequest(@Size(max = 100, message = "title 不得超过 100 个字符") String title) {
    }

    public record ConversationResponse(Long id, String title, LocalDateTime createdAt,
                                       LocalDateTime lastActivityAt) {
    }

    public record SendMessageRequest(@NotBlank(message = "content 不能为空")
                                     @Size(max = 10000, message = "content 过长")
                                     String content) {
    }

    public record MessageResponse(Long id, ConversationRole role, MessageSource source, String content,
                                  String modelName, boolean callCounted, LocalDateTime createdAt) {
    }

    public record SendMessageResponse(MessageResponse message, int dailyUsed, int dailyLimit,
                                      int monthlyUsed, int monthlyLimit) {
    }
}
