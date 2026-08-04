package com.fzdzzj.lifehabitassistant;

import com.fzdzzj.lifehabitassistant.pojo.ExportFormat;
import com.fzdzzj.lifehabitassistant.pojo.ExportReportType;
import com.fzdzzj.lifehabitassistant.pojo.ExportTask;
import com.fzdzzj.lifehabitassistant.pojo.ExportTaskStatus;
import com.fzdzzj.lifehabitassistant.pojo.User;
import com.fzdzzj.lifehabitassistant.server.dao.ExportTaskRepository;
import com.fzdzzj.lifehabitassistant.server.dao.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@ActiveProfiles("test")
class ExportTaskLifecycleRepositoryTest {
    @Autowired
    private ExportTaskRepository tasks;
    @Autowired
    private UserRepository users;
    @Autowired
    private EntityManager em;

    @Test
    void markCancelledTransitionsPendingOrRunningAndScopesToUser() {
        User owner = users.save(new User("tc-" + UUID.randomUUID(), "hash"));
        User other = users.save(new User("tco-" + UUID.randomUUID(), "hash"));
        ExportTask pending = tasks.saveAndFlush(task(owner));
        ExportTask running = tasks.saveAndFlush(task(owner));
        assertEquals(1, tasks.markRunning(running.getId(), LocalDateTime.now()));
        ExportTask foreign = tasks.saveAndFlush(task(other));

        LocalDateTime now = LocalDateTime.now();
        assertEquals(1, tasks.markCancelled(pending.getId(), owner.getId(), now));
        assertEquals(1, tasks.markCancelled(running.getId(), owner.getId(), now));
        assertEquals(0, tasks.markCancelled(foreign.getId(), owner.getId(), now));
        assertEquals(1, tasks.markCancelled(foreign.getId(), other.getId(), now));
        em.clear();
        assertEquals(ExportTaskStatus.CANCELLED, tasks.findById(pending.getId()).orElseThrow().getStatus());
        assertEquals(ExportTaskStatus.CANCELLED, tasks.findById(running.getId()).orElseThrow().getStatus());
        assertEquals(ExportTaskStatus.CANCELLED, tasks.findById(foreign.getId()).orElseThrow().getStatus());
        assertNotNull(tasks.findById(pending.getId()).orElseThrow().getCancelledAt());
    }

    @Test
    void markCancelledRejectsFinishedTask() {
        User user = users.save(new User("tcf-" + UUID.randomUUID(), "hash"));
        ExportTask succeeded = tasks.saveAndFlush(task(user));
        succeeded.succeed(new byte[]{1}, "done.xlsx");
        tasks.saveAndFlush(succeeded);

        assertEquals(0, tasks.markCancelled(succeeded.getId(), user.getId(), LocalDateTime.now()));
        em.clear();
        assertEquals(ExportTaskStatus.SUCCEEDED, tasks.findById(succeeded.getId()).orElseThrow().getStatus());
    }

    @Test
    void markRetriedMovesFailedBackToPendingAndClearsError() {
        User user = users.save(new User("tr-" + UUID.randomUUID(), "hash"));
        ExportTask failed = tasks.saveAndFlush(task(user));
        failed.fail("boom");
        tasks.saveAndFlush(failed);

        assertEquals(1, tasks.markRetried(failed.getId(), user.getId()));
        em.clear();
        ExportTask reloaded = tasks.findById(failed.getId()).orElseThrow();
        assertEquals(ExportTaskStatus.PENDING, reloaded.getStatus());
        assertNull(reloaded.getErrorMessage());

        assertEquals(0, tasks.markRetried(failed.getId(), user.getId()));
    }

    @Test
    void markSucceededAndFailedAreConditionalOnRunning() {
        User user = users.save(new User("tw-" + UUID.randomUUID(), "hash"));
        ExportTask running = tasks.saveAndFlush(task(user));
        assertEquals(1, tasks.markRunning(running.getId(), LocalDateTime.now()));

        LocalDateTime now = LocalDateTime.now();
        assertEquals(1, tasks.markSucceeded(running.getId(), "out.xlsx", new byte[]{1, 2}, now));
        em.clear();
        ExportTask succeeded = tasks.findById(running.getId()).orElseThrow();
        assertEquals(ExportTaskStatus.SUCCEEDED, succeeded.getStatus());
        assertEquals("out.xlsx", succeeded.getFileName());

        ExportTask cancelled = tasks.saveAndFlush(task(user));
        assertEquals(1, tasks.markRunning(cancelled.getId(), LocalDateTime.now()));
        assertEquals(1, tasks.markCancelled(cancelled.getId(), user.getId(), LocalDateTime.now()));
        assertEquals(0, tasks.markSucceeded(cancelled.getId(), "late.xlsx", new byte[]{3}, now));
        em.clear();
        assertEquals(ExportTaskStatus.CANCELLED, tasks.findById(cancelled.getId()).orElseThrow().getStatus());

        ExportTask failed = tasks.saveAndFlush(task(user));
        assertEquals(1, tasks.markRunning(failed.getId(), LocalDateTime.now()));
        assertEquals(1, tasks.markFailed(failed.getId(), "boom", now));
        em.clear();
        assertEquals(ExportTaskStatus.FAILED, tasks.findById(failed.getId()).orElseThrow().getStatus());
    }

    @Test
    void pagedQueriesAreScopedAndSortedByCreationDesc() throws Exception {
        User owner = users.save(new User("tl-" + UUID.randomUUID(), "hash"));
        User other = users.save(new User("tlo-" + UUID.randomUUID(), "hash"));
        ExportTask first = tasks.saveAndFlush(task(owner));
        Thread.sleep(10);
        ExportTask second = tasks.saveAndFlush(task(owner));
        Thread.sleep(10);
        ExportTask failed = tasks.saveAndFlush(task(owner));
        failed.fail("boom");
        tasks.saveAndFlush(failed);
        tasks.saveAndFlush(task(other));

        em.clear();
        Page<ExportTask> page = tasks.findByUserId(owner.getId(),
                PageRequest.of(0, 10, Sort.by("createdAt").descending()));
        assertEquals(List.of(failed.getId(), second.getId(), first.getId()),
                page.getContent().stream().map(ExportTask::getId).toList());
        assertTrue(page.getContent().stream().noneMatch(t -> t.getUser().getId().equals(other.getId())));

        Page<ExportTask> failedPage = tasks.findByUserIdAndStatus(owner.getId(), ExportTaskStatus.FAILED,
                PageRequest.of(0, 10, Sort.by("createdAt").descending()));
        assertEquals(List.of(failed.getId()), failedPage.getContent().stream().map(ExportTask::getId).toList());
    }

    @Test
    void cleanupQueryReturnsOnlySucceededTasksOlderThanCutoff() {
        User user = users.save(new User("tcu-" + UUID.randomUUID(), "hash"));
        ExportTask old = tasks.saveAndFlush(task(user));
        old.succeed(new byte[]{1}, "old.xlsx");
        tasks.saveAndFlush(old);
        ExportTask fresh = tasks.saveAndFlush(task(user));
        fresh.succeed(new byte[]{2}, "fresh.xlsx");
        tasks.saveAndFlush(fresh);
        ExportTask cancelled = tasks.saveAndFlush(task(user));
        cancelled.cancel();
        tasks.saveAndFlush(cancelled);

        em.createNativeQuery("UPDATE export_tasks SET created_at = :cutoff WHERE id = :id")
                .setParameter("cutoff", LocalDateTime.now().minusDays(30))
                .setParameter("id", old.getId())
                .executeUpdate();
        em.clear();

        List<ExportTask> expired = tasks.findByStatusAndCreatedAtBefore(
                ExportTaskStatus.SUCCEEDED, LocalDateTime.now().minusDays(7), PageRequest.of(0, 100));

        assertEquals(List.of(old.getId()), expired.stream().map(ExportTask::getId).toList());
    }

    private ExportTask task(User user) {
        return new ExportTask(user, ExportReportType.CUSTOM, ExportFormat.XLSX,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30));
    }
}
