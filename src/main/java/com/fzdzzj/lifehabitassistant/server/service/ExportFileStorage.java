package com.fzdzzj.lifehabitassistant.server.service;

import java.io.InputStream;

/**
 * Where generated export files live. Implementations must be idempotent for
 * delete and must not leak the raw storage location to callers.
 */
public interface ExportFileStorage {

    /**
     * Persists the file under the given key and returns the key as stored.
     */
    String store(String key, byte[] content);

    /**
     * Opens the file for reading. The caller is responsible for closing the
     * returned stream.
     */
    InputStream load(String key);

    /**
     * Removes the file; deleting a missing key is a no-op.
     */
    void delete(String key);
}
