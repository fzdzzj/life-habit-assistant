package com.fzdzzj.lifehabitassistant.pojo;

import java.time.LocalDateTime;
import java.util.List;

public final class AiAdviceDtos {
    private AiAdviceDtos() {
    }

    /** Structured advice content, either produced by the model or derived from rule advice on fallback. */
    public record AiAdviceContent(String periodSummary, String riskExplanation, List<String> recommendations,
                                  String nextPeriodPlan, String encouragement, String disclaimer) {
        public AiAdviceContent {
            recommendations = List.copyOf(recommendations);
        }
    }

    public record AiAdviceResponse(AdviceSource source, AiAdviceContent content, Long historyId,
                                   LocalDateTime createdAt, int dailyUsed, int dailyLimit,
                                   int monthlyUsed, int monthlyLimit) {
    }

    /** Latest saved advice attached to a report export; null when the period has no saved advice. */
    public record AdviceSnapshot(Long historyId, AdviceSource source, AiAdviceContent content,
                                 LocalDateTime createdAt) {
    }
}
