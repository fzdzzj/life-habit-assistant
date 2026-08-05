package com.fzdzzj.lifehabitassistant.server.service;

import com.fzdzzj.lifehabitassistant.config.ExportProperties;
import com.fzdzzj.lifehabitassistant.server.dao.ExportTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Moves legacy LONGBLOB export files to external storage once at startup.
 * Idempotent and batch-scoped; a single failing row is logged and skipped so
 * startup is never blocked by one bad file.
 */
@Service
public class ExportFileMigrator implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(ExportFileMigrator.class);
    private static final int BATCH_SIZE = 100;

    private final ExportTaskRepository tasks;
    private final ExportFileStorage storage;
    private final ExportProperties properties;

    public ExportFileMigrator(ExportTaskRepository tasks, ExportFileStorage storage,
                              ExportProperties properties) {
        this.tasks = tasks;
        this.storage = storage;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.backfillEnabled()) {
            log.info("Export file backfill is disabled; legacy LONGBLOB rows are left untouched");
            return;
        }
        migrate();
    }

    public int migrate() {
        int migrated = 0;
        while (true) {
            List<ExportTaskRepository.LegacyExportContent> batch = tasks.findLegacyContent(BATCH_SIZE);
            if (batch.isEmpty()) {
                break;
            }
            for (ExportTaskRepository.LegacyExportContent row : batch) {
                try {
                    String key = ExportFileKeys.key(row.getId(), row.getFileName());
                    storage.store(key, row.getFileContent());
                    if (tasks.markFileExternalized(row.getId(), key) == 1) {
                        migrated++;
                    } else {
                        // Another instance externalized the row first; remove
                        // the copy we just wrote to avoid duplicates.
                        storage.delete(key);
                    }
                } catch (RuntimeException ex) {
                    log.error("Export file migration failed for task {}", row.getId(), ex);
                }
            }
        }
        if (migrated > 0) {
            log.info("Migrated {} export files from LONGBLOB to external storage", migrated);
        }
        return migrated;
    }
}
