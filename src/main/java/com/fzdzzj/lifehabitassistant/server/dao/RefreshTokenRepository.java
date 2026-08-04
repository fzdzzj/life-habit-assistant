package com.fzdzzj.lifehabitassistant.server.dao;

import com.fzdzzj.lifehabitassistant.pojo.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Atomic single-use rotation: only the caller that wins the UPDATE may issue
     * a new token pair; a concurrent call sees 0 and is treated as reuse.
     */
    @Modifying
    @Transactional
    @Query(value = """
            UPDATE refresh_tokens
            SET revoked_at = :now
            WHERE id = :id AND revoked_at IS NULL
            """, nativeQuery = true)
    int markRevokedIfActive(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Modifying
    @Transactional
    @Query(value = """
            UPDATE refresh_tokens
            SET revoked_at = :now
            WHERE session_id = :sessionId AND revoked_at IS NULL
            """, nativeQuery = true)
    int revokeAllForSession(@Param("sessionId") Long sessionId, @Param("now") LocalDateTime now);
}
