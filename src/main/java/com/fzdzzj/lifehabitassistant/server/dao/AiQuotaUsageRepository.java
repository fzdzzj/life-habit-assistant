package com.fzdzzj.lifehabitassistant.server.dao;

import com.fzdzzj.lifehabitassistant.pojo.AiQuotaPeriod;
import com.fzdzzj.lifehabitassistant.pojo.AiQuotaUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AiQuotaUsageRepository extends JpaRepository<AiQuotaUsage, Long> {
    @Modifying
    @Query(value = """
            UPDATE ai_quota_usage
            SET used_count = used_count + 1, updated_at = :now
            WHERE user_id = :userId AND period_type = :periodType AND period_key = :periodKey
              AND used_count < :limit
            """, nativeQuery = true)
    int incrementIfBelowLimit(@Param("userId") Long userId, @Param("periodType") String periodType,
                              @Param("periodKey") String periodKey, @Param("limit") int limit,
                              @Param("now") LocalDateTime now);

    Optional<AiQuotaUsage> findByUserIdAndPeriodTypeAndPeriodKey(
            Long userId, AiQuotaPeriod periodType, String periodKey);

    /**
     * Native scalar read: the row is updated with raw SQL, so reading through the entity
     * would return a stale value from the Hibernate first-level cache in the same transaction.
     */
    @Query(value = """
            SELECT used_count
            FROM ai_quota_usage
            WHERE user_id = :userId AND period_type = :periodType AND period_key = :periodKey
            """, nativeQuery = true)
    Optional<Integer> findUsedCount(@Param("userId") Long userId, @Param("periodType") String periodType,
                                    @Param("periodKey") String periodKey);

    @Query(value = """
            SELECT COALESCE(SUM(used_count), 0)
            FROM ai_quota_usage
            WHERE period_type = :periodType AND period_key = :periodKey
            """, nativeQuery = true)
    long sumUsedCount(@Param("periodType") String periodType, @Param("periodKey") String periodKey);
}
