package com.fzdzzj.lifehabitassistant.server.dao;

import com.fzdzzj.lifehabitassistant.pojo.ExportTask;
import com.fzdzzj.lifehabitassistant.pojo.ExportTaskStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ExportTaskRepository extends JpaRepository<ExportTask, Long> {
    @EntityGraph(attributePaths = "user")
    Optional<ExportTask> findWithUserById(Long id);

    Optional<ExportTask> findByIdAndUserId(Long id, Long userId);

    long countByUserIdAndStatus(Long userId, ExportTaskStatus status);

    /**
     * Atomic PENDING -> RUNNING transition; returns 1 only for the worker that
     * wins the transition, so a task is never generated twice.
     */
    @Modifying
    @Transactional
    @Query(value = """
            UPDATE export_tasks
            SET status = 'RUNNING', started_at = :now
            WHERE id = :id AND status = 'PENDING'
            """, nativeQuery = true)
    int markRunning(@Param("id") Long id, @Param("now") LocalDateTime now);
}
