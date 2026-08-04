package com.fzdzzj.lifehabitassistant.config;

/**
 * Cache that can be invalidated per user when the underlying data changes.
 */
public interface UserDataCache {
    void evictUser(Long userId);
}
