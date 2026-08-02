package com.fzdzzj.lifehabitassistant.pojo;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_advice_history")
public class AiAdviceHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Enumerated(EnumType.STRING)
    @Column(name = "advice_type", nullable = false, length = 20)
    private AiAdviceType adviceType;
    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;
    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AdviceSource source;
    @Column(name = "model_name", length = 100)
    private String modelName;
    @Column(name = "prompt_version", nullable = false, length = 50)
    private String promptVersion;
    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;
    @Column(name = "call_counted", nullable = false)
    private boolean callCounted;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected AiAdviceHistory() {
    }

    public AiAdviceHistory(User user, AiAdviceType adviceType, LocalDate periodStart, LocalDate periodEnd,
                           AdviceSource source, String modelName, String promptVersion, String content,
                           boolean callCounted) {
        this.user = user;
        this.adviceType = adviceType;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.source = source;
        this.modelName = modelName;
        this.promptVersion = promptVersion;
        this.content = content;
        this.callCounted = callCounted;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public AiAdviceType getAdviceType() {
        return adviceType;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public AdviceSource getSource() {
        return source;
    }

    public String getModelName() {
        return modelName;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public String getContent() {
        return content;
    }

    public boolean isCallCounted() {
        return callCounted;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
