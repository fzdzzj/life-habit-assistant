package com.fzdzzj.lifehabitassistant.server.dao;

import com.fzdzzj.lifehabitassistant.pojo.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SessionRepository extends JpaRepository<UserSession, Long> {
    List<UserSession> findByUserIdAndRevokedAtIsNullOrderByLastActiveAtDesc(Long userId);

    Optional<UserSession> findByIdAndUserId(Long id, Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(value = """
            UPDATE sessions
            SET revoked_at = :now
            WHERE user_id = :userId AND revoked_at IS NULL
            """, nativeQuery = true)
    int revokeAllForUser(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(value = """
            UPDATE sessions
            SET revoked_at = :now
            WHERE id = :id AND revoked_at IS NULL
            """, nativeQuery = true)
    int revokeById(@Param("id") Long id, @Param("now") LocalDateTime now);
}
