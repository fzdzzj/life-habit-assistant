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

    interface LegacyExportContent {
        Long getId();

        String getFileName();

        byte[] getFileContent();
    }

    @EntityGraph(attributePaths = "user")
    Optional<ExportTask> findWithUserById(Long id);

    Optional<ExportTask> findByIdAndUserId(Long id, Long userId);

    long countByUserIdAndStatus(Long userId, ExportTaskStatus status);

    long countByUserId(Long userId);

    long countByStatus(ExportTaskStatus status);

    Page<ExportTask> findByUserId(Long userId, Pageable pageable);

    Page<ExportTask> findByUserIdAndStatus(Long userId, ExportTaskStatus status, Pageable pageable);

    @Query(value = "SELECT t FROM ExportTask t JOIN FETCH t.user",
            countQuery = "SELECT COUNT(t) FROM ExportTask t")
    Page<ExportTask> findAllWithUser(Pageable pageable);

    @Query(value = "SELECT t FROM ExportTask t JOIN FETCH t.user WHERE t.status = :status",
            countQuery = "SELECT COUNT(t) FROM ExportTask t WHERE t.status = :status")
    Page<ExportTask> findWithUserByStatus(@Param("status") ExportTaskStatus status, Pageable pageable);

    @Query(value = "SELECT t FROM ExportTask t JOIN FETCH t.user WHERE t.user.id = :userId",
            countQuery = "SELECT COUNT(t) FROM ExportTask t WHERE t.user.id = :userId")
    Page<ExportTask> findWithUserByUserId(@Param("userId") Long userId, Pageable pageable);

    List<ExportTask> findByStatusAndCreatedAtBefore(ExportTaskStatus status, LocalDateTime cutoff, Pageable pageable);

    /**
     * Rows still holding legacy LONGBLOB content, oldest first, for the
     * startup backfill that moves files to external storage.
     */
    @Query(value = """
            SELECT id, file_name, file_content
            FROM export_tasks
            WHERE file_path IS NULL AND file_content IS NOT NULL
            ORDER BY id
            LIMIT :limit
            """, nativeQuery = true)
    List<LegacyExportContent> findLegacyContent(@Param("limit") int limit);

    /**
     * Conditional backfill write: only succeeds when the row has not yet been
     * externalized, so concurrent instances cannot both claim the same row.
     */
    @Modifying
    @Transactional
    @Query(value = """
            UPDATE export_tasks
            SET file_path = :filePath, file_content = NULL
            WHERE id = :id AND file_path IS NULL
            """, nativeQuery = true)
    int markFileExternalized(@Param("id") Long id, @Param("filePath") String filePath);

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
     * Admin cancellation without a user scope; only PENDING/RUNNING can move.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(value = """
            UPDATE export_tasks
            SET status = 'CANCELLED', cancelled_at = :now
            WHERE id = :id AND status IN ('PENDING', 'RUNNING')
            """, nativeQuery = true)
    int markCancelledAnyUser(@Param("id") Long id, @Param("now") LocalDateTime now);

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
            SET status = 'SUCCEEDED', file_name = :fileName, file_path = :filePath,
                error_message = NULL, finished_at = :now
            WHERE id = :id AND status = 'RUNNING'
            """, nativeQuery = true)
    int markSucceeded(@Param("id") Long id, @Param("fileName") String fileName,
                      @Param("filePath") String filePath, @Param("now") LocalDateTime now);

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
