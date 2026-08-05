package com.fzdzzj.lifehabitassistant.server.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Builds storage keys for export files. Keys are derived from the task id and
 * a sanitized file name, so they are unique, traceable and safe to use as a
 * path or an object key.
 */
public final class ExportFileKeys {
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");
    private static final String SAFE_NAME_PATTERN = "[^A-Za-z0-9._-]";

    private ExportFileKeys() {
    }

    public static String key(Long taskId, String fileName) {
        String safeName = fileName == null || fileName.isBlank()
                ? "export"
                : fileName.replaceAll(SAFE_NAME_PATTERN, "_");
        return "export/" + LocalDate.now().format(MONTH) + "/" + taskId + "-" + safeName;
    }
}
