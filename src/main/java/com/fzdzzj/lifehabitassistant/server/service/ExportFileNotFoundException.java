package com.fzdzzj.lifehabitassistant.server.service;

/**
 * Raised when an export file is genuinely missing from storage, as opposed to
 * a transient storage failure. Download maps this to 404.
 */
public class ExportFileNotFoundException extends ExportFileStorageException {

    public ExportFileNotFoundException(String key, Throwable cause) {
        super("导出文件不存在: " + key, cause);
    }
}
