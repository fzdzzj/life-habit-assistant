package com.fzdzzj.lifehabitassistant.server.dao;

import com.fzdzzj.lifehabitassistant.pojo.AiAdviceHistory;
import com.fzdzzj.lifehabitassistant.pojo.AiAdviceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

public interface AiAdviceHistoryRepository extends JpaRepository<AiAdviceHistory, Long> {
    long countByUserIdAndCallCountedTrueAndCreatedAtBetween(Long userId, LocalDateTime start, LocalDateTime end);

    Optional<AiAdviceHistory> findFirstByUserIdAndAdviceTypeAndPeriodStartAndPeriodEndOrderByCreatedAtDesc(
            Long userId, AiAdviceType adviceType, LocalDate periodStart, LocalDate periodEnd);
}
