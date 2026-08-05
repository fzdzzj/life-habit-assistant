package com.fzdzzj.lifehabitassistant;

import com.fzdzzj.lifehabitassistant.server.service.ExportFileStorageException;
import com.fzdzzj.lifehabitassistant.server.service.LocalExportFileStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalExportFileStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void storeCreatesParentsAndLoadReturnsSameBytes() throws IOException {
        LocalExportFileStorage storage = new LocalExportFileStorage(tempDir);
        byte[] bytes = {1, 2, 3};

        String key = storage.store("export/202608/1-report.xlsx", bytes);

        assertTrue(Files.exists(tempDir.resolve("export/202608/1-report.xlsx")));
        try (InputStream in = storage.load(key)) {
            assertArrayEquals(bytes, in.readAllBytes());
        }
    }

    @Test
    void deleteRemovesFileAndMissingKeyIsNoOp() throws IOException {
        LocalExportFileStorage storage = new LocalExportFileStorage(tempDir);
        storage.store("export/a.xlsx", new byte[]{1});
        Path file = tempDir.resolve("export/a.xlsx");
        assertTrue(Files.exists(file));

        storage.delete("export/a.xlsx");
        assertFalse(Files.exists(file));
        storage.delete("export/a.xlsx");
    }

    @Test
    void rejectsKeysEscapingRoot() {
        LocalExportFileStorage storage = new LocalExportFileStorage(tempDir);

        assertThrows(ExportFileStorageException.class, () -> storage.store("../evil.xlsx", new byte[]{1}));
        assertThrows(ExportFileStorageException.class, () -> storage.load("export/../../evil.xlsx"));
        assertThrows(ExportFileStorageException.class, () -> storage.delete("C:/windows/system32/evil.xlsx"));
        assertThrows(ExportFileStorageException.class, () -> storage.store(null, new byte[]{1}));
        assertThrows(ExportFileStorageException.class, () -> storage.store(" ", new byte[]{1}));
    }

    @Test
    void loadMissingFileFails() {
        LocalExportFileStorage storage = new LocalExportFileStorage(tempDir);

        assertThrows(ExportFileStorageException.class, () -> storage.load("export/missing.xlsx"));
    }
}
