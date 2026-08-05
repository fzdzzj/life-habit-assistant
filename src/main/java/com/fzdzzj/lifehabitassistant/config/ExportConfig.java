package com.fzdzzj.lifehabitassistant.config;

import com.fzdzzj.lifehabitassistant.server.service.ExportFileStorage;
import com.fzdzzj.lifehabitassistant.server.service.LocalExportFileStorage;
import com.fzdzzj.lifehabitassistant.server.service.S3ExportFileStorage;
import io.minio.MinioClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
@EnableConfigurationProperties(ExportProperties.class)
public class ExportConfig {

    @Bean
    @ConditionalOnProperty(name = "app.export.storage.type", havingValue = "local", matchIfMissing = true)
    public ExportFileStorage localExportFileStorage(ExportProperties properties) {
        return new LocalExportFileStorage(Path.of(properties.storage().local().directory()));
    }

    @Bean
    @ConditionalOnProperty(name = "app.export.storage.type", havingValue = "s3")
    public ExportFileStorage s3ExportFileStorage(ExportProperties properties) {
        ExportProperties.S3 s3 = properties.storage().s3();
        MinioClient.Builder builder = MinioClient.builder()
                .endpoint(s3.endpoint())
                .credentials(s3.accessKey(), s3.secretKey());
        if (s3.region() != null && !s3.region().isBlank()) {
            builder.region(s3.region());
        }
        return new S3ExportFileStorage(builder.build(), s3.bucket());
    }
}
