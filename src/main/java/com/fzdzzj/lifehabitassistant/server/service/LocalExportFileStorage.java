package com.fzdzzj.lifehabitassistant.server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

/**
 * Filesystem-backed storage rooted at a configured directory. The key is
 * treated as a relative path; any key escaping the root is rejected.
 */
public class LocalExportFileStorage implements ExportFileStorage {
    private static final Logger log = LoggerFactory.getLogger(LocalExportFileStorage.class);

    private final Path root;

    public LocalExportFileStorage(Path directory) {
        this.root = directory.toAbsolutePath().normalize();
    }

    @Override
    public String store(String key, byte[] content) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
            log.debug("Export file stored locally key={} bytes={}", key, content.length);
            return key;
        } catch (IOException ex) {
            throw new ExportFileStorageException("导出文件写入失败: " + key, ex);
        }
    }

    @Override
    public InputStream load(String key) {
        Path target = resolve(key);
        try {
            return Files.newInputStream(target);
        } catch (NoSuchFileException ex) {
            throw new ExportFileNotFoundException(key, ex);
        } catch (IOException ex) {
            throw new ExportFileStorageException("导出文件读取失败: " + key, ex);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException ex) {
            throw new ExportFileStorageException("导出文件删除失败: " + key, ex);
        }
    }

    private Path resolve(String key) {
        if (key == null || key.isBlank()) {
            throw new ExportFileStorageException("导出文件存储键不能为空");
        }
        Path resolved = root.resolve(key).normalize();
        if (!resolved.startsWith(root)) {
            throw new ExportFileStorageException("非法导出文件存储键: " + key);
        }
        return resolved;
    }
}
