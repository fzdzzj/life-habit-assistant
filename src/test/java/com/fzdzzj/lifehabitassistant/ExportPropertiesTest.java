package com.fzdzzj.lifehabitassistant;

import com.fzdzzj.lifehabitassistant.config.ExportProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExportPropertiesTest {

    @Test
    void defaultsToLocalStorageWhenStorageMissing() {
        ExportProperties properties = new ExportProperties(1, 1, 1, true, null);

        assertEquals("local", properties.storage().type());
        assertEquals("./data/exports", properties.storage().local().directory());
    }

    @Test
    void rejectsUnknownStorageType() {
        assertThrows(IllegalArgumentException.class,
                () -> new ExportProperties(1, 1, 1, true,
                        new ExportProperties.Storage("ftp", null, null)));
    }

    @Test
    void rejectsS3WithoutRequiredSettings() {
        assertThrows(IllegalArgumentException.class,
                () -> new ExportProperties(1, 1, 1, true,
                        new ExportProperties.Storage("s3", null, null)));
        assertThrows(IllegalArgumentException.class,
                () -> new ExportProperties(1, 1, 1, true,
                        new ExportProperties.Storage("s3", null,
                                new ExportProperties.S3("https://s3.example.com", "", "secret", "bucket", null))));
    }

    @Test
    void acceptsCompleteS3Settings() {
        ExportProperties properties = new ExportProperties(1, 1, 1, false,
                new ExportProperties.Storage("s3", null,
                        new ExportProperties.S3("https://s3.example.com", "key", "secret", "bucket", "us-east-1")));

        assertEquals("s3", properties.storage().type());
        assertEquals("bucket", properties.storage().s3().bucket());
    }
}
