package com.fzdzzj.lifehabitassistant.server.dao;

import com.fzdzzj.lifehabitassistant.pojo.ExportTask;
import com.fzdzzj.lifehabitassistant.pojo.ExportTaskStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ExportTaskRepository extends JpaRepository<ExportTask, Long> {
    @EntityGraph(attributePaths = "user")
    Optional<ExportTask> findWithUserById(Long id);

    Optional<ExportTask> findByIdAndUserId(Long id, Long userId);

    long countByUserIdAndStatus(Long userId, ExportTaskStatus status);

    Page<ExportTask> findByUserId(Long userId, Pageable pageable);

    Page<ExportTask> findByUserIdAndStatus(Long userId, ExportTaskStatus status, Pageable pageable);

    List<ExportTask> findByStatusAndCreatedAtBefore(ExportTaskStatus status, LocalDateTime cutoff, Pageable pageable);

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

    /**
     * Atomic PENDING/RUNNING -> CANCELLED transition scoped to the owning user.
     * Returns 1 only when the caller wins the race against the worker.
     */
    @Modifying
    @Transactional
    @Query(value = """
            UPDATE export_tasks
            SET status = 'CANCELLED', cancelled_at = :now
            WHERE id = :id AND user_id = :userId AND status IN ('PENDING', 'RUNNING')
            """, nativeQuery = true)
    int markCancelled(@Param("id") Long id, @Param("userId") Long userId, @Param("now") LocalDateTime now);

    /**
     * Atomic FAILED -> PENDING transition scoped to the owning user; clears the
     * error message and previous execution timestamps before re-queueing.
     */
    @Modifying
    @Transactional
    @Query(value = """
            UPDATE export_tasks
            SET status = 'PENDING', error_message = NULL, started_at = NULL, finished_at = NULL
            WHERE id = :id AND user_id = :userId AND status = 'FAILED'
            """, nativeQuery = true)
    int markRetried(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * Conditional RUNNING -> SUCCEEDED write of the generated file. Returns 0
     * when the task was cancelled or otherwise changed while the worker was
     * generating, so a cancelled task never gets file content.
     */
    @Modifying
    @Transactional
    @Query(value = """
            UPDATE export_tasks
            SET status = 'SUCCEEDED', file_name = :fileName, file_content = :content,
                error_message = NULL, finished_at = :now
            WHERE id = :id AND status = 'RUNNING'
            """, nativeQuery = true)
    int markSucceeded(@Param("id") Long id, @Param("fileName") String fileName,
                      @Param("content") byte[] content, @Param("now") LocalDateTime now);

    /**
     * Conditional RUNNING -> FAILED write. Returns 0 when the task was
     * cancelled while the worker was generating, so cancellation wins.
     */
    @Modifying
    @Transactional
    @Query(value = """
            UPDATE export_tasks
            SET status = 'FAILED', error_message = :errorMessage, finished_at = :now
            WHERE id = :id AND status = 'RUNNING'
            """, nativeQuery = true)
    int markFailed(@Param("id") Long id, @Param("errorMessage") String errorMessage,
                   @Param("now") LocalDateTime now);
}
