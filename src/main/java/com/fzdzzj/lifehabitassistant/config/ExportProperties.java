package com.fzdzzj.lifehabitassistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Locale;
import java.util.Set;

/**
 * Bounds and storage settings for the asynchronous export feature. maxDays
 * guards the custom range, maxPendingPerUser prevents one user from flooding
 * the single-instance thread pool, and storage decides where generated files
 * live (local disk by default, S3-compatible object storage when configured).
 */
@ConfigurationProperties(prefix = "app.export")
public record ExportProperties(int maxDays, int maxPendingPerUser, int retentionDays,
                               boolean backfillEnabled, Storage storage) {

    public ExportProperties {
        if (maxDays < 1) {
            throw new IllegalArgumentException("app.export.max-days must be positive");
        }
        if (maxPendingPerUser < 1) {
            throw new IllegalArgumentException("app.export.max-pending-per-user must be positive");
        }
        if (retentionDays < 1) {
            throw new IllegalArgumentException("app.export.retention-days must be positive");
        }
        if (storage == null) {
            storage = new Storage("local", new Local("./data/exports"), null);
        }
        storage.validate();
    }

    public record Storage(String type, Local local, S3 s3) {

        public Storage {
            if (type == null || type.isBlank()) {
                type = "local";
            }
            type = type.trim().toLowerCase(Locale.ROOT);
            if (!Set.of("local", "s3").contains(type)) {
                throw new IllegalArgumentException("app.export.storage.type 仅支持 local 或 s3");
            }
            if ("local".equals(type) && (local == null || local.directory() == null || local.directory().isBlank())) {
                local = new Local("./data/exports");
            }
        }

        public void validate() {
            if ("s3".equals(type)) {
                if (s3 == null || isBlank(s3.endpoint()) || isBlank(s3.accessKey())
                        || isBlank(s3.secretKey()) || isBlank(s3.bucket())) {
                    throw new IllegalArgumentException(
                            "app.export.storage.s3 需要配置 endpoint、access-key、secret-key 与 bucket");
                }
            }
        }

        private static boolean isBlank(String value) {
            return value == null || value.isBlank();
        }
    }

    public record Local(String directory) {
    }

    public record S3(String endpoint, String accessKey, String secretKey, String bucket, String region) {
    }
}
