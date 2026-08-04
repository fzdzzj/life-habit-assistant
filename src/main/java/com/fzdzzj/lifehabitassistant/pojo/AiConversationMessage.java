package com.fzdzzj.lifehabitassistant.pojo;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_conversation_messages")
public class AiConversationMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private AiConversation conversation;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ConversationRole role;
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private MessageSource source;
    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;
    @Column(name = "model_name", length = 100)
    private String modelName;
    @Column(name = "call_counted", nullable = false)
    private boolean callCounted;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected AiConversationMessage() {
    }

    public AiConversationMessage(AiConversation conversation, ConversationRole role, MessageSource source,
                                 String content, String modelName, boolean callCounted) {
        this.conversation = conversation;
        this.role = role;
        this.source = source;
        this.content = content;
        this.modelName = modelName;
        this.callCounted = callCounted;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public AiConversation getConversation() {
        return conversation;
    }

    public ConversationRole getRole() {
        return role;
    }

    public MessageSource getSource() {
        return source;
    }

    public String getContent() {
        return content;
    }

    public String getModelName() {
        return modelName;
    }

    public boolean isCallCounted() {
        return callCounted;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
