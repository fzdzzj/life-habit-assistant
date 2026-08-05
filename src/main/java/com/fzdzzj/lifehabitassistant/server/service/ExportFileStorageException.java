package com.fzdzzj.lifehabitassistant.server.service;

/**
 * Raised when an export file cannot be written, read or deleted. The message
 * is safe to log; callers decide whether to translate it into an API error.
 */
public class ExportFileStorageException extends RuntimeException {

    public ExportFileStorageException(String message) {
        super(message);
    }

    public ExportFileStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
