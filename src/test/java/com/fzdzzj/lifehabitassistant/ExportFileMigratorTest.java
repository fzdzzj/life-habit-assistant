package com.fzdzzj.lifehabitassistant;

import com.fzdzzj.lifehabitassistant.config.ExportProperties;
import com.fzdzzj.lifehabitassistant.server.dao.ExportTaskRepository;
import com.fzdzzj.lifehabitassistant.server.service.ExportFileKeys;
import com.fzdzzj.lifehabitassistant.server.service.ExportFileMigrator;
import com.fzdzzj.lifehabitassistant.server.service.ExportFileStorage;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ExportFileMigratorTest {
    private static final ExportProperties PROPERTIES = new ExportProperties(1826, 5, 7, true,
            new ExportProperties.Storage("local", new ExportProperties.Local("./target/test-exports"), null));

    @Test
    void migratesAllLegacyRowsAndClearsContent() {
        ExportTaskRepository tasks = mock(ExportTaskRepository.class);
        ExportFileStorage storage = mock(ExportFileStorage.class);
        ExportTaskRepository.LegacyExportContent first = row(1L, "a.xlsx");
        ExportTaskRepository.LegacyExportContent second = row(2L, "b.xlsx");
        when(tasks.findLegacyContent(100)).thenReturn(List.of(first, second), List.of());
        when(storage.store(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(tasks.markFileExternalized(eq(1L), any())).thenReturn(1);
        when(tasks.markFileExternalized(eq(2L), any())).thenReturn(1);
        ExportFileMigrator migrator = new ExportFileMigrator(tasks, storage, PROPERTIES);

        int migrated = migrator.migrate();

        assertEquals(2, migrated);
        verify(storage, times(2)).store(any(), any());
        verify(tasks).markFileExternalized(eq(1L), any());
        verify(tasks).markFileExternalized(eq(2L), any());
    }

    @Test
    void singleRowFailureDoesNotStopTheRest() {
        ExportTaskRepository tasks = mock(ExportTaskRepository.class);
        ExportFileStorage storage = mock(ExportFileStorage.class);
        ExportTaskRepository.LegacyExportContent bad = row(1L, "bad.xlsx");
        ExportTaskRepository.LegacyExportContent good = row(2L, "good.xlsx");
        when(tasks.findLegacyContent(100)).thenReturn(List.of(bad, good), List.of());
        when(storage.store(any(), any()))
                .thenThrow(new RuntimeException("disk full"))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(tasks.markFileExternalized(eq(2L), any())).thenReturn(1);
        ExportFileMigrator migrator = new ExportFileMigrator(tasks, storage, PROPERTIES);

        int migrated = migrator.migrate();

        assertEquals(1, migrated);
        verify(tasks, never()).markFileExternalized(eq(1L), any());
        verify(tasks).markFileExternalized(eq(2L), any());
    }

    @Test
    void concurrentRowIsNotDoubleClaimedAndCopyIsRemoved() {
        ExportTaskRepository tasks = mock(ExportTaskRepository.class);
        ExportFileStorage storage = mock(ExportFileStorage.class);
        ExportTaskRepository.LegacyExportContent row = row(1L, "a.xlsx");
        when(tasks.findLegacyContent(100)).thenReturn(List.of(row), List.of());
        when(storage.store(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(tasks.markFileExternalized(eq(1L), any())).thenReturn(0);
        ExportFileMigrator migrator = new ExportFileMigrator(tasks, storage, PROPERTIES);

        int migrated = migrator.migrate();

        assertEquals(0, migrated);
        verify(storage).delete(ExportFileKeys.key(1L, "a.xlsx"));
    }

    @Test
    void startupRunnerIsSkippedWhenBackfillDisabled() {
        ExportTaskRepository tasks = mock(ExportTaskRepository.class);
        ExportFileStorage storage = mock(ExportFileStorage.class);
        ExportProperties disabled = new ExportProperties(1826, 5, 7, false,
                new ExportProperties.Storage("local", new ExportProperties.Local("./x"), null));
        ExportFileMigrator migrator = new ExportFileMigrator(tasks, storage, disabled);

        migrator.run(mock(ApplicationArguments.class));

        verifyNoInteractions(tasks, storage);
    }

    private ExportTaskRepository.LegacyExportContent row(Long id, String fileName) {
        ExportTaskRepository.LegacyExportContent row = mock(ExportTaskRepository.LegacyExportContent.class);
        when(row.getId()).thenReturn(id);
        when(row.getFileName()).thenReturn(fileName);
        when(row.getFileContent()).thenReturn(new byte[]{1, 2, 3});
        return row;
    }
}
